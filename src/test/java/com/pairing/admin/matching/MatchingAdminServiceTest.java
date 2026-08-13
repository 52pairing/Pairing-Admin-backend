package com.pairing.admin.matching;

import com.pairing.admin.matching.application.MatchingAdminService;
import com.pairing.admin.matching.infrastructure.ai.PythonEmbeddingClient;
import com.pairing.admin.matching.infrastructure.persistence.MatchingAdminRepository;
import com.pairing.admin.matching.presentation.api.response.EmbeddingMissingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI matching admin service")
class MatchingAdminServiceTest {

    @Mock
    private MatchingAdminRepository matchingAdminRepository;
    @Mock
    private PythonEmbeddingClient pythonEmbeddingClient;

    @InjectMocks
    private MatchingAdminService matchingAdminService;

    @Test
    @DisplayName("returns missing embedding summary and page")
    void findMissingEmbeddings() {
        PageRequest pageable = PageRequest.of(0, 20);
        EmbeddingMissingResponse.Item item = new EmbeddingMissingResponse.Item(
                "POSITION", 33L, "Project / BACKEND", "RECRUITING",
                "recruiting position has no embedding", null, "SUCCESS", null);

        when(matchingAdminRepository.countMissingFreelancers()).thenReturn(2L);
        when(matchingAdminRepository.countMissingPositions()).thenReturn(1L);
        when(matchingAdminRepository.findMissingEmbeddings("ALL", pageable))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        EmbeddingMissingResponse response = matchingAdminService.findMissingEmbeddings("ALL", pageable);

        assertThat(response.summary().freelancerMissingCount()).isEqualTo(2L);
        assertThat(response.summary().positionMissingCount()).isEqualTo(1L);
        assertThat(response.items().content()).containsExactly(item);
    }

    @Test
    @DisplayName("reindexes a freelancer with collected source text")
    void reindexFreelancer() {
        when(matchingAdminRepository.findFreelancerEmbeddingSource(9L))
                .thenReturn(List.of("DEVELOPMENT", "BACKEND", "JAVA", "SPRING_BOOT"));

        matchingAdminService.reindexFreelancer(9L);

        verify(pythonEmbeddingClient).upsertFreelancer(9L, "DEVELOPMENT\nBACKEND\nJAVA\nSPRING_BOOT");
    }

    @Test
    @DisplayName("skips position reindex when source text is blank")
    void reindexPositionSkipsBlankSource() {
        when(matchingAdminRepository.findPositionEmbeddingSource(23L, 33L))
                .thenReturn(Arrays.asList(null, "", "   "));

        matchingAdminService.reindexPosition(23L, 33L);

        verify(pythonEmbeddingClient, never()).upsertPosition(eq(33L), anyString());
    }
}
