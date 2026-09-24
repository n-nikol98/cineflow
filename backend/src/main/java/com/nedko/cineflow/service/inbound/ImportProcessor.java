package com.nedko.cineflow.service.inbound;

import java.time.Instant;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.XMLReader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.nedko.cineflow.repository.ImportTaskRepository;
import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.xml.MoviesXml;

/**
 * Asynchronously parses an uploaded XML file and persists its movies.
 *
 * <p>Runs on {@code importExecutor} (via {@link #process}) so {@link ImportService}'s
 * caller does not block on parsing and persistence. The XML is validated against
 * {@code import.xsd} and parsed with a hardened JAXB/SAX configuration (DOCTYPE, and
 * both general and parameter external entities are disallowed) to prevent XXE attacks.
 * Parsing failures are caught and recorded on the {@link ImportTask} rather than
 * propagated, since there is no caller left to observe an exception at this point.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class ImportProcessor {

    private static final String SCHEMA_PATH = "import.xsd";
    private static final String DISALLOW_DOCTYPE_DECL =
            "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private final ImportTaskRepository tasks;
    private final ImportWriter writer;

    private final Schema schema = loadSchema();
    private final JAXBContext jaxbContext = createJaxbContext();

    /**
     * Parses {@code bytes} as movie XML and persists its movies via {@link ImportWriter},
     * updating {@code task}'s status to {@code COMPLETED} if every movie persisted or
     * was a duplicate, or {@code FAILED} if parsing failed or any movie could not be
     * persisted — in the latter case, no movies from this file are kept (only
     * previously-resolved reference data survives; see {@link ImportWriter}), so
     * {@code recordCount} is left at {@code 0}.
     *
     * @param id the import task's identifier
     * @param bytes the raw XML file content to parse
     */
    @Async("importExecutor")
    void process(final Long id, final byte[] bytes) {
        log.info("Starting import for task {}", id);
        tasks.updateStatus(id, ImportTask.Status.PROCESSING);
        final ImportTask task = tasks.findByIdOrThrow(id);

        int recordCount = 0;
        try {
            final MoviesXml moviesXml = parse(bytes);
            recordCount = writer.write(task, moviesXml);
            task.setStatus(ImportTask.Status.COMPLETED);
            log.info("Import task {} completed successfully with {} record(s) persisted",
                    id, recordCount);
        } catch (final JAXBException exception) {
            // The outer UnmarshalException itself carries no message for XSD violations or not-well-formed XML;
            // the real problem is reported via getLinkedException() (typically a SAXParseException).
            final Throwable linkedException = exception.getLinkedException();
            fail(task, id, nonBlankMessageOrElse(linkedException == null ? exception : linkedException, 
                JAXBException.class.getSimpleName()), exception);
        } catch (final DataIntegrityViolationException exception) {
            // ImportWriter's own Javadoc explains this: a movie in this file lost a concurrent
            // insert race against another import persisting the exact same (title, director,
            // release year) at the same time. The raw JDBC/Hibernate message is unreadable, so
            // replace it with an explanation the user can act on (just re-upload the file).
            fail(task, id, "One or more movies in this file were being imported concurrently by "
                    + "another file at the same time and lost the race to persist first. No "
                    + "movies from this file were saved; simply re-upload it once the other "
                    + "import has finished.", exception);
        } catch (final Exception exception) {
            fail(task, id, nonBlankMessageOrElse(exception, exception.getClass().getSimpleName()), exception);
        } finally {
            task.setRecordCount(recordCount);
            task.setCompletedAt(Instant.now());
        }

        tasks.save(task);
    }

    /**
     * Marks {@code task} as {@code FAILED} with {@code message} and logs the underlying
     * {@code exception}.
     *
     * @param task the task to fail
     * @param id the task's identifier, for logging
     * @param message the human-readable failure description to record on the task
     * @param exception the underlying failure, logged alongside {@code message}
     */
    private void fail(final ImportTask task, final Long id, final String message, final Exception exception) {
        task.setStatus(ImportTask.Status.FAILED);
        task.setErrorMessage(message);
        log.error("Import task {} failed: {}", id, message, exception);
    }

    /**
     * Unmarshals {@code bytes} into a {@link MoviesXml}, validating it against the
     * import XSD.
     *
     * @param bytes the raw XML file content
     * @return the parsed, schema-validated movies document
     * @throws JAXBException if unmarshalling fails
     * @throws SAXException if XML parsing fails
     */
    private MoviesXml parse(final byte[] bytes) throws JAXBException, SAXException {
        final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        unmarshaller.setSchema(schema);
        return (MoviesXml) unmarshaller.unmarshal(createSecureSource(bytes));
    }

    /**
     * Loads and compiles the import XSD ({@value #SCHEMA_PATH}) for schema validation.
     *
     * @return the compiled schema
     */
    private static Schema loadSchema() {
        try {
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            final ClassPathResource resource = new ClassPathResource(SCHEMA_PATH);
            return factory.newSchema(new StreamSource(resource.getInputStream()));
        } catch (final IOException | SAXException exception) {
            throw new IllegalStateException("Could not load XML import schema.", exception);
        }
    }

    /**
     * Creates the JAXB context used to unmarshal {@link MoviesXml} documents.
     *
     * @return the JAXB context for {@link MoviesXml}
     */
    private static JAXBContext createJaxbContext() {
        try {
            return JAXBContext.newInstance(MoviesXml.class);
        } catch (final JAXBException exception) {
            throw new IllegalStateException("Could not create XML binding context.", exception);
        }
    }

    /**
     * Builds a hardened {@link SAXSource} over {@code bytes} with external entity
     * resolution and DOCTYPE declarations disabled, to prevent XXE attacks.
     *
     * @param bytes the raw XML file content
     * @return a secure SAX source ready for JAXB unmarshalling
     * @throws SAXException if the secure XML reader could not be configured
     */
    private static SAXSource createSecureSource(final byte[] bytes) throws SAXException {
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature(DISALLOW_DOCTYPE_DECL, true);
            factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
            factory.setFeature(LOAD_EXTERNAL_DTD, false);
            final XMLReader reader = factory.newSAXParser().getXMLReader();
            return new SAXSource(reader, new InputSource(new ByteArrayInputStream(bytes)));
        } catch (final Exception exception) {
            throw new SAXException("Could not configure secure XML reader.", exception);
        }
    }

    /**
     * Returns {@code throwable}'s message, or {@code fallback} if it has none.
     *
     * @param throwable the exception whose message to use
     * @param fallback the value to return if {@code throwable} has no message
     * @return {@code throwable}'s message, or {@code fallback}
     */
    private static String nonBlankMessageOrElse(final Throwable throwable, final String fallback) {
        final String message = throwable.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}