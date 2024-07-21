package net.shasankp000.GraphicalUserInterface;

import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.Model;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import net.shasankp000.GraphicalUserInterface.Widgets.DropdownMenuWidget;
import net.shasankp000.OllamaClient.ollamaClient;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class CustomScreen extends Screen {

        public Screen parent;
        private TextFieldWidget textFieldWidget;
        private DropdownMenuWidget dropdownMenuWidget;


        public CustomScreen(Text title, Screen parent) {
            super(title);
            this.parent = parent;
        }

        @Override
        protected void init() {

            List<Model> models;
            List<String> modelList = new ArrayList<>();

            if (ollamaClient.pingOllamaServer()) {

                String host = "http://localhost:11434/";

                OllamaAPI ollamaAPI = new OllamaAPI(host);

                try {
                    models = ollamaAPI.listModels();
                } catch (OllamaBaseException | IOException | InterruptedException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }


                for (Model model: models) {

                    modelList.add(model.getName());

                }
            }

            DropdownMenuWidget dropdownMenuWidget = new DropdownMenuWidget(80, 40, 200, 20, Text.of("List of available models"), modelList);
            this.dropdownMenuWidget = dropdownMenuWidget;
            this.addSelectableChild(dropdownMenuWidget);


            ButtonWidget buttonWidget2 = ButtonWidget.builder(Text.of("Close"), (btn1) -> {
                        this.close();
                    }
            ).dimensions(this.width - 150, 40, 120, 20).build();

            ButtonWidget buttonWidget3 = ButtonWidget.builder(Text.of("Save"), (btn1) -> {
                        this.close();
                    }
            ).dimensions(this.width - 150, 200, 120, 20).build();

            // x, y, width, height
            // It's recommended to use the fixed height of 20 to prevent rendering issues with the button
            // textures.

            // Register the button widget.

            this.addDrawableChild(buttonWidget2);
            this.addDrawableChild(dropdownMenuWidget);
            this.addDrawableChild(buttonWidget3);

        }

//
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);

            // Minecraft doesn't have a "label" widget, so we'll have to draw our own text.
            // We'll subtract the font height from the Y position to make the text appear above the button.
            // Subtracting an extra 10 pixels will give the text some padding.
            // textRenderer, text, x, y, color, hasShadow
            context.drawText(this.textRenderer, "AI-Player Mod configuration Menu",140, 20 - this.textRenderer.fontHeight - 10, 0xFFFFFFFF, true);
            context.drawText(this.textRenderer, "Select Language Model",20, this.dropdownMenuWidget.getY()  - this.textRenderer.fontHeight - 10, 0xFFFFFFFF, true);
        }

        @Override
        public void close() {
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }

    }



