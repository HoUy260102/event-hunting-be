package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import com.example.event.constant.EventStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Event {
    @Id
    @UlidID
    private String id;
    private String name;
    @Column(columnDefinition = "TEXT")
    private String descriptionHtml;
    @Column(columnDefinition = "TEXT")
    private String descriptionText;

    private String location;
    @Enumerated(EnumType.STRING)
    private EventStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long minPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id")
    private Province province;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banner_id", unique = true)
    private File banner;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poster_id", unique = true)
    private File poster;


    private String organizerName;
    @Column(columnDefinition = "TEXT")
    private String organizerInfo;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_logo_id", unique = true)
    private File organizerLogo;

    @OneToMany(mappedBy = "event")
    private List<Show> shows;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
