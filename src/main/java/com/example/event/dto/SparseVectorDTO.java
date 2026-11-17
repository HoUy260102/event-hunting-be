package com.example.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO đại diện cho Sparse Vector theo chuẩn cấu trúc của Qdrant:
 * {
 *   "indices": [1042, 8492, 3948],
 *   "values":  [1.0,  1.37, 2.1]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SparseVectorDTO {

    @Builder.Default
    private List<Integer> indices = new ArrayList<>();

    @Builder.Default
    private List<Float> values = new ArrayList<>();

    public boolean isEmpty() {
        return indices == null || indices.isEmpty() || values == null || values.isEmpty();
    }

    public static SparseVectorDTO empty() {
        return new SparseVectorDTO(new ArrayList<>(), new ArrayList<>());
    }
}

