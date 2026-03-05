package io.github.skydynamic.quickbackupmulti.client.screen;

import io.github.skydynamic.quickbackupmulti.translate.Translate;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RestoreScreen extends Screen {
    private final Button cancelButton;
    @Setter
    private String state = Translate.tr("quickbackupmulti.screen.restore_screen.waiting_for_server");
    @Setter
    private float progress = 0;

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
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        guiGraphics.drawCenteredString(font, Component.nullToEmpty(this.state), centerX, centerY - 20, 0xFFFFFF);
        drawProgressBar(
            guiGraphics,
            centerX - 70,
            centerY - 5,
            centerX + 70,
            centerY + 5
        );
        guiGraphics.drawCenteredString(
            font,
            Component.nullToEmpty(Translate.tr("quickbackupmulti.screen.restore_screen.progress", this.getPercentString())),
            centerX,
            centerY + 10,
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

    private String getPercentString() {
        return String.format("%.2f", this.progress * 100);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1) {
        int barX0 = x0 + 2;
        int barY0 = y0 + 2;
        int barX1 = x0 + (int) ((x1 - x0) * progress);
        int barY1 = y1 - 2;

        int progressBarColor = colorFromRatio(progress, true);

        guiGraphics.fill(x0, y0, x1, y1, -1);
        guiGraphics.fill(barX0, barY0, barX1, barY1, progressBarColor);
    }

    private static int colorFromRatio(double ratio, boolean oneIsGreen) {
        double p = ratio;

        if (!oneIsGreen) {
            p = 1 - p;
        }

        int r = (int) (255d * (Math.max(0, Math.min(2 - 2 * p, 1))));
        int g = (int) (255d * (Math.max(0, Math.min(2 * p, 1))));

        return 0xFF000000 + (r << 16) + (g << 8);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        this.renderPanorama(guiGraphics, f);
        this.renderBlurredBackground();
        this.renderMenuBackground(guiGraphics);
    }
}
