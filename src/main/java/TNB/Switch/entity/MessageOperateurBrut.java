package TNB.Switch.entity;


import TNB.Switch.enums.IaClassification;
import TNB.Switch.enums.MatchingStatus;
import TNB.Switch.enums.MessageProcessingStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Message opérateur brut reçu d'un device (SMS/notification USSD).
 * Le contenu brut original est conservé en permanence, quel que soit
 * le résultat du traitement — c'est la preuve d'audit (CDC §12).
 */
@Entity
@Table(name = "message_operateur_brut", indexes = {
        @Index(name = "idx_msg_processing_status", columnList = "processing_status"),
        @Index(name = "idx_msg_device", columnList = "device_id"),
        @Index(name = "idx_msg_operateur", columnList = "operateur_id"),
        @Index(name = "idx_msg_received_at", columnList = "received_at"),
        // Composite : c'est exactement la combinaison utilisée par
        // findMatchingCandidates (device + opérateur + fenêtre temporelle).
        @Index(name = "idx_msg_device_operateur_received", columnList = "device_id, operateur_id, received_at")
})
public class MessageOperateurBrut extends BaseLedgerEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false, updatable = false)
    private Device device;

    @ManyToOne(optional = false)
    @JoinColumn(name = "operateur_id", nullable = false, updatable = false)
    private Operateur operateur;

    @Column(name = "raw_content", nullable = false, updatable = false, length = 1000)
    private String rawContent;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 25)
    private MessageProcessingStatus processingStatus = MessageProcessingStatus.PENDING_AI;

    // ===== Résultat du service IA =====

    @Enumerated(EnumType.STRING)
    @Column(name = "ia_classification", length = 20)
    private IaClassification iaClassification;

    @Column(name = "ia_confidence")
    private Double iaConfidence;

    @Column(name = "ia_extracted_amount", precision = 19, scale = 0)
    private BigDecimal iaExtractedAmount;

    @Column(name = "ia_extracted_phone_number", length = 20)
    private String iaExtractedPhoneNumber;

    @Column(name = "ia_extracted_reference", length = 100)
    private String iaExtractedReference;

    @Column(name = "ia_model_version", length = 30)
    private String iaModelVersion;

    @Column(name = "ia_retry_count", nullable = false)
    private int iaRetryCount = 0;

    // ===== Résultat du matching déterministe backend =====

    @Enumerated(EnumType.STRING)
    @Column(name = "matching_status", length = 20)
    private MatchingStatus matchingStatus;

    // Nullable tant que non réconcilié — devient la commande retenue
    // une fois le matching déterministe résolu à un candidat unique.
    @ManyToOne
    @JoinColumn(name = "matched_commande_id")
    private Commande matchedCommande;

    protected MessageOperateurBrut() {
        // requis par JPA
    }

    public MessageOperateurBrut(Device device, Operateur operateur,
                                String rawContent, Instant receivedAt) {
        this.device = device;
        this.operateur = operateur;
        this.rawContent = rawContent;
        this.receivedAt = receivedAt;
    }

    public Device getDevice() { return device; }
    public Operateur getOperateur() { return operateur; }
    public String getRawContent() { return rawContent; }
    public Instant getReceivedAt() { return receivedAt; }

    public MessageProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(MessageProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public IaClassification getIaClassification() { return iaClassification; }
    public void setIaClassification(IaClassification iaClassification) {
        this.iaClassification = iaClassification;
    }

    public Double getIaConfidence() { return iaConfidence; }
    public void setIaConfidence(Double iaConfidence) { this.iaConfidence = iaConfidence; }

    public BigDecimal getIaExtractedAmount() { return iaExtractedAmount; }
    public void setIaExtractedAmount(BigDecimal iaExtractedAmount) {
        this.iaExtractedAmount = iaExtractedAmount;
    }

    public String getIaExtractedPhoneNumber() { return iaExtractedPhoneNumber; }
    public void setIaExtractedPhoneNumber(String iaExtractedPhoneNumber) {
        this.iaExtractedPhoneNumber = iaExtractedPhoneNumber;
    }

    public String getIaExtractedReference() { return iaExtractedReference; }
    public void setIaExtractedReference(String iaExtractedReference) {
        this.iaExtractedReference = iaExtractedReference;
    }

    public String getIaModelVersion() { return iaModelVersion; }
    public void setIaModelVersion(String iaModelVersion) { this.iaModelVersion = iaModelVersion; }

    public int getIaRetryCount() { return iaRetryCount; }
    public void incrementIaRetryCount() { this.iaRetryCount++; }

    public MatchingStatus getMatchingStatus() { return matchingStatus; }
    public void setMatchingStatus(MatchingStatus matchingStatus) {
        this.matchingStatus = matchingStatus;
    }

    public Commande getMatchedCommande() { return matchedCommande; }
    public void setMatchedCommande(Commande matchedCommande) {
        this.matchedCommande = matchedCommande;
    }
}