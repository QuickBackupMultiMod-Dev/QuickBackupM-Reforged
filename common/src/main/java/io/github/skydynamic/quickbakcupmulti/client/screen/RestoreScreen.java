package io.github.skydynamic.quickbakcupmulti.client.screen;

import io.github.skydynamic.quickbakcupmulti.translate.Translate;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RestoreScreen extends Screen {
    private final Button cancelButton;
    @Setter
    private String state = Translate.tr("quickbackupmulti.screen.restore_screen.title");
    @Setter
    private String progress = "0%";

    public RestoreScreen(Button.OnPress onCancelButtonPress) {
        super(Component.nullToEmpty(Translate.tr("quickbackupmulti.screen.restore_screen.title")));
        cancelButton = Button.builder(Component.nullToEmpty(Translate.tr("quickbackupmulti.screen.restore_screen.cancel_button")), onCancelButtonPress).build();
    }

    @Override
    protected void init() {
        cancelButton.setPosition(this.width / 2 - cancelButton.getWidth() / 2, this.height / 2 + 40);

        this.addRenderableWidget(this.cancelButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        guiGraphics.drawCenteredString(font, Component.nullToEmpty(this.state), this.width / 2, this.height / 2, 0xFFFFFF);
        guiGraphics.drawCenteredString(
            font,
            Component.nullToEmpty(Translate.tr("quickbackupmulti.screen.restore_screen.progress") + this.progress),
            this.width / 2,
            this.height / 2 + 20,
            0xFFFFFF
        );
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected boolean shouldNarrateNavigation() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        this.renderPanorama(guiGraphics, f);
        this.renderBlurredBackground();
        this.renderMenuBackground(guiGraphics);
    }
}
