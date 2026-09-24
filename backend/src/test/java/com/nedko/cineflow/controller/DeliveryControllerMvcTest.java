package com.nedko.cineflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.nedko.cineflow.domain.MovieDelivery.Status;
import com.nedko.cineflow.dto.DeliveryStatusDto;
import com.nedko.cineflow.exception.handler.GlobalExceptionHandler;
import com.nedko.cineflow.service.outbound.DeliveryScheduler;

class DeliveryControllerMvcTest {

    private static final String DELIVERIES_PATH = "/deliveries";
    private static final String FAILED_PATH = DELIVERIES_PATH + "/failed";
    private static final long MOVIE_ID = 4L;

    private DeliveryScheduler deliveries;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        deliveries = Mockito.mock(DeliveryScheduler.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeliveryController(deliveries))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void failedReturnsRequestedPageOfFailedDeliveries() throws Exception {
        final List<DeliveryStatusDto> allDeliveries = fiveFailedDeliveries();
        final int pageSize = 2;
        final List<DeliveryStatusDto> secondPage = allDeliveries.subList(pageSize, pageSize * 2);
        when(deliveries.findFailed(any())).thenReturn(
                new PageImpl<>(secondPage, PageRequest.of(1, pageSize), allDeliveries.size()));

        mockMvc.perform(get(FAILED_PATH).param("page", "1").param("size", String.valueOf(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(pageSize))
                .andExpect(jsonPath("$.content[0].movieId").value(secondPage.get(0).movieId()))
                .andExpect(jsonPath("$.content[1].movieId").value(secondPage.get(1).movieId()))
                .andExpect(jsonPath("$.totalElements").value(allDeliveries.size()))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void failedWithoutPaginationParamsReturnsAllFailedDeliveriesOnADefaultPage() throws Exception {
        final List<DeliveryStatusDto> allDeliveries = fiveFailedDeliveries();
        when(deliveries.findFailed(any())).thenReturn(
                new PageImpl<>(allDeliveries, PageRequest.of(0, 20), allDeliveries.size()));

        mockMvc.perform(get(FAILED_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(allDeliveries.size()))
                .andExpect(jsonPath("$.content[0].movieId").value(allDeliveries.get(0).movieId()))
                .andExpect(jsonPath("$.content[4].movieId").value(allDeliveries.get(4).movieId()));
    }

    @Test
    void statusReturnsDeliveryStatusForMovie() throws Exception {
        when(deliveries.status(MOVIE_ID)).thenReturn(new DeliveryStatusDto(MOVIE_ID, Status.PENDING, 0, null, ""));

        mockMvc.perform(get(DELIVERIES_PATH + "/" + MOVIE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieId").value(MOVIE_ID))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void retryReturnsNoContentAndDelegatesToScheduler() throws Exception {
        mockMvc.perform(post(DELIVERIES_PATH + "/" + MOVIE_ID + "/retry"))
                .andExpect(status().isNoContent());

        verify(deliveries).retry(MOVIE_ID);
    }

    private static List<DeliveryStatusDto> fiveFailedDeliveries() {
        return List.of(
                failedDelivery(1L),
                failedDelivery(2L),
                failedDelivery(3L),
                failedDelivery(MOVIE_ID),
                failedDelivery(5L));
    }

    private static DeliveryStatusDto failedDelivery(long movieId) {
        return new DeliveryStatusDto(movieId, Status.FAILED, 3, null, "delivery failed");
    }
}
