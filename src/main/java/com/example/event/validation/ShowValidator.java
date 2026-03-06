package com.example.event.validation;

import com.example.event.constant.SeatMapType;
import com.example.event.constant.TicketTierStatus;
import com.example.event.dto.request.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ShowValidator {

    public Map<String, String> validate(List<CreateShowReq> shows) {
        if (shows == null || shows.isEmpty()) return new HashMap<>();

        Map<String, String> details = new HashMap<>();
        Map<Integer, Set<String>> showOverlapMap = new HashMap<>();

        for (int i = 0; i < shows.size(); i++) {
            CreateShowReq currentShow = shows.get(i);
            String showPath = "shows[" + i + "]";

            //Check logic maxOrder >= minOrder
            if (currentShow.getMinOrder() > currentShow.getMaxOrder()) {
                details.put(showPath + ".maxOrder", "Số lượng tối đa không được nhỏ hơn tối thiểu");
            }

            // Check Logic thời gian Show (Start < End)
            if (currentShow.getStartTime() != null && currentShow.getEndTime() != null) {
                if (!currentShow.getEndTime().isAfter(currentShow.getStartTime())) {
                    details.put(showPath + ".endTime", "Thời gian kết thúc phải sau thời gian bắt đầu");
                }
            }

            // Check Logic SeatMap & SVG
            List<SeatMapType> typesRequireSvg = Arrays.asList(SeatMapType.SECTION_ONLY, SeatMapType.SECTION_WITH_SEATS);
            if (typesRequireSvg.contains(currentShow.getSeatMapType())) {
                if (currentShow.getSeatMapSvg() == null || currentShow.getSeatMapSvg().trim().equals("")) {
                    details.put(showPath + ".seatMapSvg", "Vui lòng cấu hình sơ đồ ghế cho loại hình này");
                }
            }

            //Validate các TicketTypes bên trong Show
            validateTicketTypes(currentShow, i, details);

            //Gom dữ liệu để check trùng lặp Show
            for (int j = i + 1; j < shows.size(); j++) {
                CreateShowReq otherShow = shows.get(j);
                if (isTimeOverlap(currentShow.getStartTime(), currentShow.getEndTime(),
                        otherShow.getStartTime(), otherShow.getEndTime())) {
                    showOverlapMap.computeIfAbsent(i, k -> new HashSet<>()).add("Suất " + (j + 1));
                    showOverlapMap.computeIfAbsent(j, k -> new HashSet<>()).add("Suất " + (i + 1));
                }
            }
        }

        showOverlapMap.forEach((index, conflicts) -> {
            details.put("shows[" + index + "].startTime",
                    "Thời gian bị trùng với: " + String.join(", ", conflicts));
        });

        return details;
    }

    private void validateTicketTypes(CreateShowReq show, int showIdx, Map<String, String> details) {
        if (show.getTicketTypes() == null) return;
        Set<String> ticketTypeNames = new HashSet<>();
        Set<String> ticketTypeSectionIds = new HashSet<>();
        for (int tIdx = 0; tIdx < show.getTicketTypes().size(); tIdx++) {
            CreateTicketTypeReq ticketType = show.getTicketTypes().get(tIdx);
            String ticketPath = String.format("shows[%d].ticketTypes[%d]", showIdx, tIdx);

            //Logic kiểm tra tên có trùng không
            if (ticketType.getName() != null) {
                if (ticketTypeNames.contains(ticketType.getName().trim())) {
                    details.put(ticketPath + ".name", "Tên loại vé đã tồn tại trong suất diễn này");
                } else {
                    ticketTypeNames.add(ticketType.getName());
                }
            }

            List<SeatMapType> typesRequireSvg = Arrays.asList(SeatMapType.SECTION_ONLY, SeatMapType.SECTION_WITH_SEATS);
            if (typesRequireSvg.contains(show.getSeatMapType())) {
                if (ticketType.getSectionId() == null || ticketType.getSectionId().trim().equals("")) {
                    details.put(ticketPath + ".sectionId", "Vui lòng chọn khu vực trên sơ đồ");
                }
            }

            //Kiểm tra section id phải duy nhất
            if (ticketType.getSectionId() != null && !ticketType.getSectionId().trim().equals("")) {
                if (ticketTypeSectionIds.contains(ticketType.getSectionId().trim())) {
                    details.put(ticketPath + ".sectionId", "sectionId này bị trùng");
                } else {
                    ticketTypeSectionIds.add(ticketType.getSectionId());
                }
            }

            Map<Integer, Set<String>> tierOverlapMap = new HashMap<>();
            List<CreateTicketTierReq> tiers = ticketType.getTicketTiers();

            if (tiers == null || tiers.isEmpty()) continue;

            for (int k = 0; k < tiers.size(); k++) {
                var currentTier = tiers.get(k);
                String tierPath = ticketPath + ".ticketTiers[" + k + "]";
                //Check logic thời gian bắt đầu bán vé nhỏ hơn thời gian kết thúc bán vé
                if (currentTier.getSaleStartTime() != null && currentTier.getSaleEndTime() != null) {
                    if (!currentTier.getSaleEndTime().isAfter(currentTier.getSaleStartTime())) {
                        details.put(tierPath + ".saleEndTime", "Thời gian kết thúc bán phải sau thời gian bắt đầu");
                    }
                }

                //Tier End < Show Start
                if (show.getStartTime() != null && currentTier.getSaleEndTime() != null) {
                    if (currentTier.getSaleEndTime().isAfter(show.getStartTime())) {
                        details.put(tierPath + ".saleEndTime", "Vé phải kết thúc bán trước khi suất diễn bắt đầu");
                    }
                }

                // Check trùng lặp giữa các Tier trong cùng 1 TicketType
                for (int m = k + 1; m < tiers.size(); m++) {
                    var otherTier = tiers.get(m);
                    if (isTimeOverlap(currentTier.getSaleStartTime(), currentTier.getSaleEndTime(),
                            otherTier.getSaleStartTime(), otherTier.getSaleEndTime())) {
                        tierOverlapMap.computeIfAbsent(k, x -> new HashSet<>()).add(otherTier.getName());
                        tierOverlapMap.computeIfAbsent(m, x -> new HashSet<>()).add(currentTier.getName());
                    }
                }
            }

            // Đưa lỗi trùng Tier vào details
            tierOverlapMap.forEach((tierIdx, names) -> {
                details.put(ticketPath + ".ticketTiers[" + tierIdx + "].saleStartTime",
                        "Thời gian trùng với: " + String.join(", ", names));
            });
        }
    }

    public Map<String, String> validateUpdate(List<UpdateShowReq> shows) {
        if (shows == null || shows.isEmpty()) return new HashMap<>();

        Map<String, String> details = new HashMap<>();
        Map<Integer, Set<String>> showOverlapMap = new HashMap<>();

        for (int i = 0; i < shows.size(); i++) {
            UpdateShowReq currentShow = shows.get(i);
            String showPath = "shows[" + i + "]";

            //Check logic maxOrder >= minOrder
            if (currentShow.getMinOrder() > currentShow.getMaxOrder()) {
                details.put(showPath + ".maxOrder", "Số lượng tối đa không được nhỏ hơn tối thiểu");
            }

            // Check Logic thời gian Show (Start < End)
            if (currentShow.getStartTime() != null && currentShow.getEndTime() != null) {
                if (!currentShow.getEndTime().isAfter(currentShow.getStartTime())) {
                    details.put(showPath + ".endTime", "Thời gian kết thúc phải sau thời gian bắt đầu");
                }
            }

            // Check Logic SeatMap & SVG
            List<SeatMapType> typesRequireSvg = Arrays.asList(SeatMapType.SECTION_ONLY, SeatMapType.SECTION_WITH_SEATS);
            if (typesRequireSvg.contains(currentShow.getSeatMapType())) {
                if (currentShow.getSeatMapSvg() == null || currentShow.getSeatMapSvg().trim().equals("")) {
                    details.put(showPath + ".seatMapSvg", "Vui lòng cấu hình sơ đồ ghế cho loại hình này");
                }
            }

            //Validate các TicketTypes bên trong Show
            validateTicketTypesUpdate(currentShow, i, details);

            //Gom dữ liệu để check trùng lặp Show
            for (int j = i + 1; j < shows.size(); j++) {
                UpdateShowReq otherShow = shows.get(j);
                if (isTimeOverlap(currentShow.getStartTime(), currentShow.getEndTime(),
                        otherShow.getStartTime(), otherShow.getEndTime())) {
                    showOverlapMap.computeIfAbsent(i, k -> new HashSet<>()).add("Suất " + (j + 1));
                    showOverlapMap.computeIfAbsent(j, k -> new HashSet<>()).add("Suất " + (i + 1));
                }
            }
        }

        showOverlapMap.forEach((index, conflicts) -> {
            details.put("shows[" + index + "].startTime",
                    "Thời gian bị trùng với: " + String.join(", ", conflicts));
        });

        return details;
    }

    private void validateTicketTypesUpdate(UpdateShowReq show, int showIdx, Map<String, String> details) {
        if (show.getTicketTypes() == null) return;
        Set<String> ticketTypeNames = new HashSet<>();
        Set<String> ticketTypeSectionIds = new HashSet<>();
        for (int tIdx = 0; tIdx < show.getTicketTypes().size(); tIdx++) {
            UpdateTicketTypeReq ticketType = show.getTicketTypes().get(tIdx);
            String ticketPath = String.format("shows[%d].ticketTypes[%d]", showIdx, tIdx);

            //Logic kiểm tra tên có trùng không
            if (ticketType.getName() != null) {
                if (ticketTypeNames.contains(ticketType.getName().trim())) {
                    details.put(ticketPath + ".name", "Tên loại vé đã tồn tại trong suất diễn này");
                } else {
                    ticketTypeNames.add(ticketType.getName());
                }
            }

            List<SeatMapType> typesRequireSvg = Arrays.asList(SeatMapType.SECTION_ONLY, SeatMapType.SECTION_WITH_SEATS);
            if (typesRequireSvg.contains(show.getSeatMapType())) {
                if (ticketType.getSectionId() == null || ticketType.getSectionId().trim().equals("")) {
                    details.put(ticketPath + ".sectionId", "Vui lòng chọn khu vực trên sơ đồ");
                }
            }

            //Kiểm tra section id phải duy nhất
            if (ticketType.getSectionId() != null && !ticketType.getSectionId().trim().equals("")) {
                if (ticketTypeSectionIds.contains(ticketType.getSectionId().trim())) {
                    details.put(ticketPath + ".sectionId", "sectionId này bị trùng");
                } else {
                    ticketTypeSectionIds.add(ticketType.getSectionId());
                }
            }

            Map<Integer, Set<String>> tierOverlapMap = new HashMap<>();
            List<UpdateTicketTierReq> tiers = ticketType.getTicketTiers();

            if (tiers == null || tiers.isEmpty()) continue;

            for (int k = 0; k < tiers.size(); k++) {
                var currentTier = tiers.get(k);
                String tierPath = ticketPath + ".ticketTiers[" + k + "]";
                //Check logic thời gian bắt đầu bán vé nhỏ hơn thời gian kết thúc bán vé
                if (currentTier.getSaleStartTime() != null && currentTier.getSaleEndTime() != null) {
                    if (!currentTier.getSaleEndTime().isAfter(currentTier.getSaleStartTime())) {
                        details.put(tierPath + ".saleEndTime", "Thời gian kết thúc bán phải sau thời gian bắt đầu");
                    }
                }

                //Tier End < Show Start
                if (show.getStartTime() != null && currentTier.getSaleEndTime() != null) {
                    if (currentTier.getSaleEndTime().isAfter(show.getStartTime())) {
                        details.put(tierPath + ".saleEndTime", "Vé phải kết thúc bán trước khi suất diễn bắt đầu");
                    }
                }

                // Check trùng lặp giữa các Tier trong cùng 1 TicketType
                for (int m = k + 1; m < tiers.size(); m++) {
                    var otherTier = tiers.get(m);
                    if (isTimeOverlap(currentTier.getSaleStartTime(), currentTier.getSaleEndTime(),
                            otherTier.getSaleStartTime(), otherTier.getSaleEndTime())) {
                        tierOverlapMap.computeIfAbsent(k, x -> new HashSet<>()).add(otherTier.getName());
                        tierOverlapMap.computeIfAbsent(m, x -> new HashSet<>()).add(currentTier.getName());
                    }
                }
            }

            // Đưa lỗi trùng Tier vào details
            tierOverlapMap.forEach((tierIdx, names) -> {
                details.put(ticketPath + ".ticketTiers[" + tierIdx + "].saleStartTime",
                        "Thời gian trùng với: " + String.join(", ", names));
            });
        }
    }

    public Map<String, String> validateShowForUpdate(UpdateShowReq show) {
        if (show == null) return new HashMap<>();
        Map<String, String> details = new HashMap<>();
        UpdateShowReq currentShow = show;

        //Check logic maxOrder >= minOrder
        if (currentShow.getMinOrder() > currentShow.getMaxOrder()) {
            details.put("maxOrder", "Số lượng tối đa không được nhỏ hơn tối thiểu");
        }

        // Check Logic thời gian Show (Start < End)
        if (currentShow.getStartTime() != null && currentShow.getEndTime() != null) {
            if (!currentShow.getEndTime().isAfter(currentShow.getStartTime())) {
                details.put("endTime", "Thời gian kết thúc phải sau thời gian bắt đầu");
            }
        }

        // Check Logic SeatMap & SVG
        List<SeatMapType> typesRequireSvg = Arrays.asList(SeatMapType.SECTION_ONLY, SeatMapType.SECTION_WITH_SEATS);
        if (typesRequireSvg.contains(currentShow.getSeatMapType())) {
            if (currentShow.getSeatMapSvg() == null || currentShow.getSeatMapSvg().trim().equals("")) {
                details.put("seatMapSvg", "Vui lòng cấu hình sơ đồ ghế cho loại hình này");
            }
        }

        //Validate các TicketTypes bên trong Show
        validateTicketTypesForUpdateOneShow(currentShow, details);
        return details;
    }

    private void validateTicketTypesForUpdateOneShow(UpdateShowReq show, Map<String, String> details) {
        if (show.getTicketTypes() == null) return;
        Set<String> ticketTypeNames = new HashSet<>();
        Set<String> ticketTypeSectionIds = new HashSet<>();
        for (int tIdx = 0; tIdx < show.getTicketTypes().size(); tIdx++) {
            UpdateTicketTypeReq ticketType = show.getTicketTypes().get(tIdx);
            String ticketPath = String.format("ticketTypes[%d]", tIdx);

            //Logic kiểm tra tên có trùng không
            if (ticketType.getName() != null) {
                if (ticketTypeNames.contains(ticketType.getName().trim())) {
                    details.put(ticketPath + ".name", "Tên loại vé đã tồn tại trong suất diễn này");
                } else {
                    ticketTypeNames.add(ticketType.getName());
                }
            }

            List<SeatMapType> typesRequireSvg = Arrays.asList(SeatMapType.SECTION_ONLY, SeatMapType.SECTION_WITH_SEATS);
            if (typesRequireSvg.contains(show.getSeatMapType())) {
                if (ticketType.getSectionId() == null || ticketType.getSectionId().trim().equals("")) {
                    details.put(ticketPath + ".sectionId", "Vui lòng chọn khu vực trên sơ đồ");
                }
            }

            //Kiểm tra section id phải duy nhất
            if (ticketType.getSectionId() != null && !ticketType.getSectionId().trim().equals("")) {
                if (ticketTypeSectionIds.contains(ticketType.getSectionId().trim())) {
                    details.put(ticketPath + ".sectionId", "sectionId này bị trùng");
                } else {
                    ticketTypeSectionIds.add(ticketType.getSectionId());
                }
            }

            Map<Integer, Set<String>> tierOverlapMap = new HashMap<>();
            List<UpdateTicketTierReq> tiers = ticketType.getTicketTiers();

            if (tiers == null || tiers.isEmpty()) continue;

            for (int k = 0; k < tiers.size(); k++) {
                var currentTier = tiers.get(k);
                String tierPath = ticketPath + ".ticketTiers[" + k + "]";
                //Check logic thời gian bắt đầu bán vé nhỏ hơn thời gian kết thúc bán vé
                if (currentTier.getSaleStartTime() != null && currentTier.getSaleEndTime() != null) {
                    if (!currentTier.getSaleEndTime().isAfter(currentTier.getSaleStartTime())) {
                        details.put(tierPath + ".saleEndTime", "Thời gian kết thúc bán phải sau thời gian bắt đầu");
                    }
                }
                //Tier End < Show Start
                if (show.getStartTime() != null && currentTier.getSaleEndTime() != null) {
                    if (currentTier.getSaleEndTime().isAfter(show.getStartTime())) {
                        details.put(tierPath + ".saleEndTime", "Vé phải kết thúc bán trước khi suất diễn bắt đầu");
                    }
                }
                if (currentTier.getStatus() == TicketTierStatus.INACTIVE) continue;
                // Check trùng lặp giữa các Tier trong cùng 1 TicketType
                for (int m = k + 1; m < tiers.size(); m++) {
                    var otherTier = tiers.get(m);
                    if (otherTier.getStatus() == TicketTierStatus.INACTIVE) continue;
                    if (isTimeOverlap(currentTier.getSaleStartTime(), currentTier.getSaleEndTime(),
                            otherTier.getSaleStartTime(), otherTier.getSaleEndTime())) {
                        tierOverlapMap.computeIfAbsent(k, x -> new HashSet<>()).add(otherTier.getName());
                        tierOverlapMap.computeIfAbsent(m, x -> new HashSet<>()).add(currentTier.getName());
                    }
                }
            }

            // Đưa lỗi trùng Tier vào details
            tierOverlapMap.forEach((tierIdx, names) -> {
                details.put(ticketPath + ".ticketTiers[" + tierIdx + "].saleStartTime",
                        "Thời gian trùng với: " + String.join(", ", names));
            });
        }
    }

    public Map<String, String> validateShowForCreate(CreateShowReq show) {
        if (show == null) return new HashMap<>();
        Map<String, String> details = new HashMap<>();
        CreateShowReq currentShow = show;

        //Check logic maxOrder >= minOrder
        if (currentShow.getMinOrder() > currentShow.getMaxOrder()) {
            details.put("maxOrder", "Số lượng tối đa không được nhỏ hơn tối thiểu");
        }

        // Check Logic thời gian Show (Start < End)
        if (currentShow.getStartTime() != null && currentShow.getEndTime() != null) {
            if (!currentShow.getEndTime().isAfter(currentShow.getStartTime())) {
                details.put("endTime", "Thời gian kết thúc phải sau thời gian bắt đầu");
            }
        }

        // Check Logic SeatMap & SVG
        List<SeatMapType> typesRequireSvg = Arrays.asList(SeatMapType.SECTION_ONLY, SeatMapType.SECTION_WITH_SEATS);
        if (typesRequireSvg.contains(currentShow.getSeatMapType())) {
            if (currentShow.getSeatMapSvg() == null || currentShow.getSeatMapSvg().trim().equals("")) {
                details.put("seatMapSvg", "Vui lòng cấu hình sơ đồ ghế cho loại hình này");
            }
        }

        //Validate các TicketTypes bên trong Show
        validateTicketTypesForCreateOneShow(currentShow, details);
        return details;
    }

    private void validateTicketTypesForCreateOneShow(CreateShowReq show, Map<String, String> details) {
        if (show.getTicketTypes() == null) return;
        Set<String> ticketTypeNames = new HashSet<>();
        Set<String> ticketTypeSectionIds = new HashSet<>();
        for (int tIdx = 0; tIdx < show.getTicketTypes().size(); tIdx++) {
            CreateTicketTypeReq ticketType = show.getTicketTypes().get(tIdx);
            String ticketPath = String.format("ticketTypes[%d]", tIdx);

            //Logic kiểm tra tên có trùng không
            if (ticketType.getName() != null) {
                if (ticketTypeNames.contains(ticketType.getName().trim())) {
                    details.put(ticketPath + ".name", "Tên loại vé đã tồn tại trong suất diễn này");
                } else {
                    ticketTypeNames.add(ticketType.getName());
                }
            }

            List<SeatMapType> typesRequireSvg = Arrays.asList(SeatMapType.SECTION_ONLY, SeatMapType.SECTION_WITH_SEATS);
            if (typesRequireSvg.contains(show.getSeatMapType())) {
                if (ticketType.getSectionId() == null || ticketType.getSectionId().trim().equals("")) {
                    details.put(ticketPath + ".sectionId", "Vui lòng chọn khu vực trên sơ đồ");
                }
            }

            //Kiểm tra section id phải duy nhất
            if (ticketType.getSectionId() != null && !ticketType.getSectionId().trim().equals("")) {
                if (ticketTypeSectionIds.contains(ticketType.getSectionId().trim())) {
                    details.put(ticketPath + ".sectionId", "sectionId này bị trùng");
                } else {
                    ticketTypeSectionIds.add(ticketType.getSectionId());
                }
            }

            Map<Integer, Set<String>> tierOverlapMap = new HashMap<>();
            List<CreateTicketTierReq> tiers = ticketType.getTicketTiers();

            if (tiers == null || tiers.isEmpty()) continue;

            for (int k = 0; k < tiers.size(); k++) {
                var currentTier = tiers.get(k);
                String tierPath = ticketPath + ".ticketTiers[" + k + "]";
                //Check logic thời gian bắt đầu bán vé nhỏ hơn thời gian kết thúc bán vé
                if (currentTier.getSaleStartTime() != null && currentTier.getSaleEndTime() != null) {
                    if (!currentTier.getSaleEndTime().isAfter(currentTier.getSaleStartTime())) {
                        details.put(tierPath + ".saleEndTime", "Thời gian kết thúc bán phải sau thời gian bắt đầu");
                    }
                }

                //Tier End < Show Start
                if (show.getStartTime() != null && currentTier.getSaleEndTime() != null) {
                    if (currentTier.getSaleEndTime().isAfter(show.getStartTime())) {
                        details.put(tierPath + ".saleEndTime", "Vé phải kết thúc bán trước khi suất diễn bắt đầu");
                    }
                }

                // Check trùng lặp giữa các Tier trong cùng 1 TicketType
                for (int m = k + 1; m < tiers.size(); m++) {
                    var otherTier = tiers.get(m);
                    if (isTimeOverlap(currentTier.getSaleStartTime(), currentTier.getSaleEndTime(),
                            otherTier.getSaleStartTime(), otherTier.getSaleEndTime())) {
                        tierOverlapMap.computeIfAbsent(k, x -> new HashSet<>()).add(otherTier.getName());
                        tierOverlapMap.computeIfAbsent(m, x -> new HashSet<>()).add(currentTier.getName());
                    }
                }
            }

            // Đưa lỗi trùng Tier vào details
            tierOverlapMap.forEach((tierIdx, names) -> {
                details.put(ticketPath + ".ticketTiers[" + tierIdx + "].saleStartTime",
                        "Thời gian trùng với: " + String.join(", ", names));
            });
        }
    }

    private boolean isTimeOverlap(LocalDateTime s1, LocalDateTime e1, LocalDateTime s2, LocalDateTime e2) {
        if (s1 == null || e1 == null || s2 == null || e2 == null) return false;
        return s1.isBefore(e2) && e1.isAfter(s2);
    }
}