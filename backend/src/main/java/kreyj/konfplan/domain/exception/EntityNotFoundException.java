package kreyj.konfplan.domain.exception;

import kreyj.konfplan.persistence.IdEntity;

public class EntityNotFoundException extends BusinessException {
    private final String className;


    public EntityNotFoundException(Class<? extends IdEntity> entityClass) {
        className = entityClass.getName();
    }


    public EntityNotFoundException(Class<? extends IdEntity> entityClass, String message, Throwable cause) {
        super(message, cause);
        className = entityClass.getName();
    }


    public EntityNotFoundException(Class<? extends IdEntity> entityClass, String message) {
        super(message);
        className = entityClass.getName();
    }
}
