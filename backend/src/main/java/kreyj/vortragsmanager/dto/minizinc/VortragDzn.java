package kreyj.vortragsmanager.dto.minizinc;

import java.util.List;

public record VortragDzn(long id, String name, long referentId, List<List<Long>> moegliche_slot_ids) {
}
