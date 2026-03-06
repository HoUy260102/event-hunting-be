package com.example.event.service.Impl;

import com.example.event.dto.ProvinceDTO;
import com.example.event.mapper.ProvinceMapper;
import com.example.event.repository.ProvinceRepository;
import com.example.event.service.ProvinceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProvinceServiceImpl implements ProvinceService {
    private final ProvinceRepository provinceRepository;
    private final ProvinceMapper provinceMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProvinceDTO> findAllProvinces() {
        List<ProvinceDTO> provinceDTOS = provinceRepository.findAll().stream().map(provinceMapper::toDTO).collect(Collectors.toList());
        return provinceDTOS;
    }
}
