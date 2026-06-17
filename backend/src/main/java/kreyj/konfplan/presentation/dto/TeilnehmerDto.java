package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class TeilnehmerDto {
    public Long id;
    public String firstName;
    public String lastName;
    public Set<String> gruppen;


    public String fullname() {
        if (StringUtils.isBlank(firstName)) {
            if (StringUtils.isBlank(lastName)) {
                return "NONAME";
            } else {
                return lastName;
            }
        } else if (StringUtils.isBlank(lastName)) {
            return firstName;
        } else {
            return lastName + ", " + firstName;
        }
    }


    public String gName() {
        return String.format("%s (%s)", fullname(),
                gruppen.stream().sorted().collect(Collectors.joining(",")));
    }


    @Override
    public String toString() {
        return fullname();
    }
}
