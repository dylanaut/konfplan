package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
@SuppressWarnings("unused")
public class NachrichtSendenDto {
    public List<Long> empfaengerIds;
    public String titel;
    public String inhalt;
}
