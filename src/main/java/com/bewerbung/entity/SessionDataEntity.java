package com.bewerbung.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Хранит данные одной сессии в Oracle (вакансия, CV, state, вывод, фото).
 * Одна запись на сессию.
 */
@Entity
@Table(name = "BEWERB_SESSION_DATA",
       indexes = @Index(name = "IDX_BEWERB_SESS_ID", columnList = "session_id", unique = true))
public class SessionDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Lob
    @Column(name = "vacancy_txt")
    private String vacancyText;

    @Lob
    @Column(name = "cv_txt")
    private String cvText;

    @Lob
    @Column(name = "state_json")
    private String stateJson;

    @Lob
    @Column(name = "anschreiben_txt")
    private String anschreibenTxt;

    @Lob
    @Column(name = "anschreiben_md")
    private String anschreibenMd;

    @Lob
    @Column(name = "lebenslauf_html")
    private String lebenslaufHtml;

    @Lob
    @Column(name = "notes_json")
    private String notesJson;

    @Lob
    @Column(name = "analysis_md")
    private String analysisMd;

    @Lob
    @Column(name = "photo_blob")
    private byte[] photoBlob;

    @Column(name = "photo_mime", length = 64)
    private String photoMime;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void setUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getVacancyText() { return vacancyText; }
    public void setVacancyText(String vacancyText) { this.vacancyText = vacancyText; }
    public String getCvText() { return cvText; }
    public void setCvText(String cvText) { this.cvText = cvText; }
    public String getStateJson() { return stateJson; }
    public void setStateJson(String stateJson) { this.stateJson = stateJson; }
    public String getAnschreibenTxt() { return anschreibenTxt; }
    public void setAnschreibenTxt(String anschreibenTxt) { this.anschreibenTxt = anschreibenTxt; }
    public String getAnschreibenMd() { return anschreibenMd; }
    public void setAnschreibenMd(String anschreibenMd) { this.anschreibenMd = anschreibenMd; }
    public String getLebenslaufHtml() { return lebenslaufHtml; }
    public void setLebenslaufHtml(String lebenslaufHtml) { this.lebenslaufHtml = lebenslaufHtml; }
    public String getNotesJson() { return notesJson; }
    public void setNotesJson(String notesJson) { this.notesJson = notesJson; }
    public String getAnalysisMd() { return analysisMd; }
    public void setAnalysisMd(String analysisMd) { this.analysisMd = analysisMd; }
    public byte[] getPhotoBlob() { return photoBlob; }
    public void setPhotoBlob(byte[] photoBlob) { this.photoBlob = photoBlob; }
    public String getPhotoMime() { return photoMime; }
    public void setPhotoMime(String photoMime) { this.photoMime = photoMime; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
