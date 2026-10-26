package kreyj.konfplan.adapter.in.web;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Markiert Endpunkte, die ohne gueltige Lizenz gesperrt werden (Planerstellung, Import, neue
 * Veranstaltungen und Nutzer) - siehe {@link LizenzFilter}.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LizenzPflichtig {
}
