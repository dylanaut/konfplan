package kreyj.konfplan.infrastructure;


import org.eclipse.microprofile.config.inject.ConfigProperties;

@ConfigProperties(prefix = "minizinc")
@SuppressWarnings("unused")
public class MiniZincConfig {
    public String path;
}
