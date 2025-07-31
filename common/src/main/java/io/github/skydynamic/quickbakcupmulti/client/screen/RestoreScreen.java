package io.github.skydynamic.quickbakcupmulti.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.skydynamic.quickbakcupmulti.translate.Translate;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

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
        float centerX = this.width / 2f;
        float centerY = this.height / 2f;
        guiGraphics.drawCenteredString(font, Component.nullToEmpty(this.state), (int) centerX, (int) centerY - 20, 0xFFFFFF);
        drawProgressBar(
            guiGraphics.pose(),
            centerX - 70,
            centerY - 5,
            centerX + 70,
            centerY + 5
        );
        guiGraphics.drawCenteredString(
            font,
            Component.nullToEmpty(Translate.tr("quickbackupmulti.screen.restore_screen.progress", this.getPercentString())),
            (int) centerX,
            (int) (centerY + 10),
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

    private void drawProgressBar(PoseStack poseStack, float x0, float y0, float x1, float y1) {
        Matrix4f pose = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        );

        float barX0 = x0 + 2;
        float barY0 = y0 + 2;
        float barX1 = x0 + (x1 - x0) * progress;
        float barY1 = y1 - 2;

        int progressBarColor = colorFromRatio(progress, true);

        builder.addVertex(pose, x0, y0, 0.1f)
            .setColor(0xffffffff);
        builder.addVertex(pose, x0, y1, 0.1f)
            .setColor(0xffffffff);
        builder.addVertex(pose, x1, y1, 0.1f)
            .setColor(0xffffffff);
        builder.addVertex(pose, x1, y0, 0.1f)
            .setColor(0xffffffff);

        builder.addVertex(pose, barX0, barY0, 0.1f)
            .setColor(progressBarColor);
        builder.addVertex(pose, barX0, barY1, 0.1f)
            .setColor(progressBarColor);
        builder.addVertex(pose, barX1, barY1, 0.1f)
            .setColor(progressBarColor);
        builder.addVertex(pose, barX1, barY0, 0.1f)
            .setColor(progressBarColor);

        MeshData data = builder.build();
        if (data == null) return;
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(data);
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
        this.renderBlurredBackground(f);
        this.renderMenuBackground(guiGraphics);
    }
}
