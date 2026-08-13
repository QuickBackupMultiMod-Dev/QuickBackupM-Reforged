package io.github.skydynamic.quickbackupmulti.mixin;

import io.github.skydynamic.quickbackupmulti.database.H2ClassNameCompat;
import org.h2.mvstore.type.MetaType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MetaType.class, remap = false)
public class MixinH2MetaType {
    @Redirect(
        method = "read(Ljava/nio/ByteBuffer;)Lorg/h2/mvstore/type/DataType;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Class;forName(Ljava/lang/String;)Ljava/lang/Class;"
        )
    )
    private Class<?> redirectClassForName(String name) throws ClassNotFoundException {
        return H2ClassNameCompat.forName(name);
    }
}
