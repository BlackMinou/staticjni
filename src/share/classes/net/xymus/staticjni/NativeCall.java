package net.xymus.staticjni;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Target(ElementType.METHOD)
public @interface NativeCall {
	public String value();
}
