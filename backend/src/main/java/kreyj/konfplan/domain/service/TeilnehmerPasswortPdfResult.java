package kreyj.konfplan.domain.service;

import java.util.List;

public record TeilnehmerPasswortPdfResult(byte[] pdf, List<String> failedLoginNames) {
}
