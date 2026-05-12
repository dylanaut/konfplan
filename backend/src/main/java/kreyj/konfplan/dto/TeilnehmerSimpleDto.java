package kreyj.konfplan.dto;

public class TeilnehmerSimpleDto {
    public Long id;
    public String firstName;
    public String lastName;
    public String gruppe;

    public TeilnehmerSimpleDto(Long id, String firstName, String lastName, String gruppe) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.gruppe = gruppe;
    }
}
