package com.example.expensetracker.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Data
@Builder
public class PageResponseDto<Item> {
    private List<Item> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <Source, Target> PageResponseDto<Target> fromPage(Page<Source> source, Function<Source, Target> mapper) {
        return PageResponseDto.<Target>builder()
                .content(source.getContent().stream().map(mapper).toList())
                .page(source.getNumber())
                .size(source.getSize())
                .totalElements(source.getTotalElements())
                .totalPages(source.getTotalPages())
                .build();
    }
}
