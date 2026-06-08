package kreyj.konfplan.infrastructure;


import org.eclipse.microprofile.config.inject.ConfigProperties;

@ConfigProperties(prefix = "minizinc")
public interface MiniZincConfig {
    String path();
}