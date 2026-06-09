package com.example.event;

import com.example.event.entity.Event;
import com.example.event.repository.EventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class EventSearchTest {

    static {
        // Load environment variables from .env file for testing environment
        io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
    }

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void testSearchScores() {
        System.out.println("=== INDEXES ON EVENT TABLE ===");
        try {
            List<Object[]> indexes = entityManager.createNativeQuery("SHOW INDEX FROM event").getResultList();
            for (Object[] idx : indexes) {
                System.out.println("Table: " + idx[0] + " | Non_unique: " + idx[1] + " | Key_name: " + idx[2] + 
                                   " | Seq_in_index: " + idx[3] + " | Column_name: " + idx[4] + 
                                   " | Index_type: " + idx[10]);
            }
        } catch (Exception e) {
            System.err.println("Failed to show indexes: " + e.getMessage());
        }

        System.out.println("=== REBUILDING FTS INDEXES ===");
        try {
            entityManager.createNativeQuery("ALTER TABLE event DROP INDEX idx_fulltext_info").executeUpdate();
        } catch (Exception e) {
            System.err.println("Failed to drop idx_fulltext_info: " + e.getMessage());
        }
        try {
            entityManager.createNativeQuery("ALTER TABLE event DROP INDEX idx_fulltext_description").executeUpdate();
        } catch (Exception e) {
            System.err.println("Failed to drop idx_fulltext_description: " + e.getMessage());
        }
        try {
            entityManager.createNativeQuery("ALTER TABLE event ADD FULLTEXT INDEX idx_fulltext_info (name, location, organizer_name)").executeUpdate();
            System.out.println("Re-created idx_fulltext_info.");
        } catch (Exception e) {
            System.err.println("Failed to create idx_fulltext_info: " + e.getMessage());
        }
        try {
            entityManager.createNativeQuery("ALTER TABLE event ADD FULLTEXT INDEX idx_fulltext_description (description_text)").executeUpdate();
            System.out.println("Re-created idx_fulltext_description.");
        } catch (Exception e) {
            System.err.println("Failed to create idx_fulltext_description: " + e.getMessage());
        }

        System.out.println("=== SEARCH SCORES FOR 'WORKSHOP' AFTER REBUILD ===");
        String sql = "SELECT id, name, location, organizer_name, " +
                     "((MATCH(name, location, organizer_name) AGAINST ('WORKSHOP' IN NATURAL LANGUAGE MODE) * 2) + " +
                     "(MATCH(description_text) AGAINST ('WORKSHOP' IN NATURAL LANGUAGE MODE))) AS score " +
                     "FROM event";
        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        for (Object[] row : rows) {
            System.out.println("ID: " + row[0] + " | Name: " + row[1] + " | Location: " + row[2] + " | Organizer: " + row[3] + " | Score: " + row[4]);
        }
        
        System.out.println("=== FULLTEXT RESULTS ===");
        List<Event> results = eventRepository.searchFullTextBoolean("WORKSHOP");
        System.out.println("Results size: " + results.size());
        for (Event e : results) {
            System.out.println("Matched Event ID: " + e.getId() + " | Name: " + e.getName());
        }
    }

    @Test
    void inspectTexts() {
        System.out.println("=== SUBSTRING CHECK FOR 'WORKSHOP' ===");
        eventRepository.findAll().forEach(e -> {
            boolean inName = e.getName() != null && e.getName().toLowerCase().contains("workshop");
            boolean inLoc = e.getLocation() != null && e.getLocation().toLowerCase().contains("workshop");
            boolean inOrg = e.getOrganizerName() != null && e.getOrganizerName().toLowerCase().contains("workshop");
            boolean inDesc = e.getDescriptionText() != null && e.getDescriptionText().toLowerCase().contains("workshop");
            System.out.println("ID: " + e.getId() + " | Name: " + e.getName() + 
                               " | inName: " + inName + " | inLoc: " + inLoc + " | inOrg: " + inOrg + " | inDesc: " + inDesc);
        });
    }
}
