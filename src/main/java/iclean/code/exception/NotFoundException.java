package iclean.code.exception;

import javax.annotation.Nullable;

public class NotFoundException extends IllegalArgumentException {
    public NotFoundException(@Nullable String message) {
        super(message);
    }
}
