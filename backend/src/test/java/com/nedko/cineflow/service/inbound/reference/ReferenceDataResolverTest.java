package com.nedko.cineflow.service.inbound.reference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.nedko.cineflow.domain.Genre;
import com.nedko.cineflow.repository.ActorRepository;
import com.nedko.cineflow.repository.DirectorRepository;
import com.nedko.cineflow.repository.GenreRepository;

/**
 * Verifies that {@link ReferenceDataResolver} recovers from the lost side of a
 * concurrent create race (see its class Javadoc) by re-querying and reusing the row
 * the winning task just committed, instead of propagating the constraint violation.
 */
@ExtendWith(MockitoExtension.class)
class ReferenceDataResolverTest {

    private static final String ACTION_GENRE = "Action";
    private static final String DRAMA_GENRE = "Drama";
    private static final String COMEDY_GENRE = "Comedy";

    @Mock
    private ActorRepository actors;
    @Mock
    private DirectorRepository directors;
    @Mock
    private GenreRepository genres;
    @Mock
    private ReferenceDataCreator creator;

    @Test
    void reusesGenreCreatedByConcurrentWinnerAfterConstraintViolation() {
        final ReferenceDataResolver resolver =
                new ReferenceDataResolver(actors, directors, genres, creator);
        final Genre winnersGenre = new Genre(ACTION_GENRE);

        // Our own lookup finds nothing yet...
        when(genres.findByName(ACTION_GENRE))
                .thenReturn(Optional.empty())
                // ...but by the time we re-query after losing the race, the winning
                // task's genre is visible.
                .thenReturn(Optional.of(winnersGenre));
        // Our own attempt to create it loses the race: a concurrent task's isolated
        // transaction committed first, so ours fails its unique constraint.
        when(creator.createGenre(ACTION_GENRE))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        final Genre resolved = resolver.findOrCreateGenre(ACTION_GENRE);

        assertSame(winnersGenre, resolved);
        verify(genres, times(2)).findByName(ACTION_GENRE);
    }

    @Test
    void createsGenreWhenNoConcurrentWinnerExists() {
        final ReferenceDataResolver resolver =
                new ReferenceDataResolver(actors, directors, genres, creator);
        final Genre created = new Genre(DRAMA_GENRE);

        when(genres.findByName(DRAMA_GENRE)).thenReturn(Optional.empty());
        when(creator.createGenre(DRAMA_GENRE)).thenReturn(created);

        final Genre resolved = resolver.findOrCreateGenre(DRAMA_GENRE);

        assertSame(created, resolved);
        verify(genres, times(1)).findByName(DRAMA_GENRE);
    }

    @Test
    void reusesExistingGenreWithoutAttemptingCreation() {
        final ReferenceDataResolver resolver =
                new ReferenceDataResolver(actors, directors, genres, creator);
        final Genre existing = new Genre(COMEDY_GENRE);

        when(genres.findByName(COMEDY_GENRE)).thenReturn(Optional.of(existing));

        final Genre resolved = resolver.findOrCreateGenre(COMEDY_GENRE);

        assertSame(existing, resolved);
        verifyNoInteractions(creator);
    }
}
