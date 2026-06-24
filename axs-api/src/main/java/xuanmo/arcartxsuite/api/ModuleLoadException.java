package xuanmo.arcartxsuite.api;

/**
 * 模块加载过程中的异常。
 */
public class ModuleLoadException extends Exception {

    public ModuleLoadException(String message) {
        super(message);
    }

    public ModuleLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
