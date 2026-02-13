package kreyj.vortragsmanager.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
public class SpeakerAvailability extends PanacheEntity {
    @ManyToOne
    public User speaker;
    
    @ManyToOne
    public EventSlot slot;
    
    public boolean isAvailable; 
}