package kreyj.konfplan.domain.service;

import java.util.List;

public record TeilnehmerPasswortZipResult(byte[] zip, List<String> failedLoginNames) {
}
