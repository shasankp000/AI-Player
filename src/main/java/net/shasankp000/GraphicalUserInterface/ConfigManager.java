package net.shasankp000.GraphicalUserInterface;


import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.shasankp000.Exception.ollamaNotReachableException;
import net.shasankp000.AIPlayer;
import net.shasankp000.FilingSystem.AIPlayerConfigModel;
import net.shasankp000.GraphicalUserInterface.Widgets.DropdownMenuWidget;
import net.shasankp000.FilingSystem.getLanguageModels;
import net.shasankp000.Network.configNetworkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.List;

public class ConfigManager extends Screen {

        public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
        public Screen parent;
        private DropdownMenuWidget dropdownMenuWidget;
        public AIPlayerConfigModel aiPlayerConfigModel = new AIPlayerConfigModel();

        public ConfigManager(Text title, Screen parent) {
            super(title);
            this.parent = parent;
        }

        @Override
        protected void init() {
            
            List<String> modelList = new ArrayList<>();

            try {
                modelList = getLanguageModels.get();
            } catch (ollamaNotReachableException e) {
                LOGGER.error("{}", e.getMessage());
            }


            DropdownMenuWidget dropdownMenuWidget = new DropdownMenuWidget(100, 40, 200, 20, Text.of("List of available models"), modelList);
            this.dropdownMenuWidget = dropdownMenuWidget;
            this.addSelectableChild(dropdownMenuWidget);


            ButtonWidget buttonWidget2 = ButtonWidget.builder(Text.of("Close"), (btn1) -> {
                        this.close();
                    }
            ).dimensions(this.width - 120, 40, 120, 20).build();

            ButtonWidget buttonWidget3 = ButtonWidget.builder(Text.of("Save"), (btn1) -> {

                        this.saveToFile();

                        if (this.client != null) {
                            this.client.getToastManager().add(
                            SystemToast.create(this.client, SystemToast.Type.NARRATOR_TOGGLE, Text.of("Settings saved!"), Text.of("Saved settings.")));
                        }

            }
            ).dimensions(this.width - 150, 200, 120, 20).build();

            // x, y, width, height
            // It's recommended to use the fixed height of 20 to prevent rendering issues with the button
            // textures.


            this.addDrawableChild(buttonWidget2);
            this.addDrawableChild(dropdownMenuWidget);
            this.addDrawableChild(buttonWidget3);

        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);

            // Minecraft doesn't have a "label" widget, so we'll have to draw our own text.
            // We'll subtract the font height from the Y position to make the text appear above the button.
            // Subtracting an extra 10 pixels will give the text some padding.
            // textRenderer, text, x, y, color, hasShadow

            int yellow = 0xFFFFFF00;
            int white = 0xFFFFFFFF;
            int green = 0xFF00FF00;
            int red = 0xFFFF0000;

            context.drawText(this.textRenderer, "AI-Player Mod configuration Menu v1.0.4-beta-1",95, 20 - this.textRenderer.fontHeight - 10, white, true);
            context.drawText(this.textRenderer, "Select Language Model",5, this.dropdownMenuWidget.getY() + 5, yellow, true);
            context.drawText(this.textRenderer, "Currently selected language model: " + AIPlayer.CONFIG.selectedLanguageModel(),100, this.dropdownMenuWidget.getY() + 30, green, true);
            context.drawText(this.textRenderer, "No need to restart the game after changing/selecting a language model!",20, this.dropdownMenuWidget.getY() + 60, red, true);
        }

        @Override
        public void close() {
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }

        private void saveToFile() {

            String modelName = this.dropdownMenuWidget.getSelectedOption();

            System.out.println(modelName);

            aiPlayerConfigModel.setSelectedLanguageModel(modelName);

            AIPlayer.CONFIG.selectedLanguageModel(modelName);
            AIPlayer.CONFIG.save(); // save to client first

            configNetworkManager.sendSaveConfigPacket(modelName); // send save packet to server

            close();

            assert this.client != null;
            this.client.setScreen(new ConfigManager(Text.empty(), this.parent));

        }

    }



