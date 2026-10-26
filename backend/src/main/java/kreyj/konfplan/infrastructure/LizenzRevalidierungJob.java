package kreyj.konfplan.infrastructure;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import kreyj.konfplan.domain.lizenz.LizenzServerException;
import kreyj.konfplan.domain.service.LizenzStatusService;
import org.jboss.logging.Logger;

/**
 * Erneuert eine Online-Lizenz rechtzeitig vor Ablauf der Revalidierungsfrist. Scheitert der Versuch
 * (Server nicht erreichbar), laeuft die Grace-Period weiter - der naechste Lauf versucht es erneut.
 */
@ApplicationScoped
public class LizenzRevalidierungJob {
    private static final Logger LOG = Logger.getLogger(LizenzRevalidierungJob.class);

    private final LizenzStatusService lizenzStatusService;

    public LizenzRevalidierungJob(LizenzStatusService lizenzStatusService) {
        this.lizenzStatusService = lizenzStatusService;
    }

    @Scheduled(every = "6h", delayed = "1m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void revalidiere() {
        try {
            lizenzStatusService.revalidiereFallsFaellig();
        } catch (LizenzServerException e) {
            LOG.warnf("Lizenz-Revalidierung fehlgeschlagen (%s): %s", e.getArt(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Unerwarteter Fehler bei der Lizenz-Revalidierung", e);
        }
    }
}
