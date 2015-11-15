/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-11-15
 */

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AssetViewer extends Application {
    public static void main(String[] args){
        Application.launch(args);
    }

    public static Path getAssetsPath(){
        TextInputDialog dialog = new TextInputDialog(Paths.get(System.getenv("APPDATA"), ".minecraft").toAbsolutePath().toString());
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(500);
        dialog.setTitle("AssetViewer");
        dialog.setHeaderText("Your Minecraft path");

        while(true){
            String pathString = dialog.showAndWait().orElse(null);
            if(pathString == null) return null;

            Path minecraftPath = Paths.get(pathString);
            if(Files.notExists(minecraftPath) || !Files.isDirectory(minecraftPath)) continue;

            Path assetsPath = minecraftPath.resolve("assets");
            if(Files.notExists(assetsPath) || !Files.isDirectory(assetsPath)) continue;

            Path indexesPath = assetsPath.resolve("indexes");
            if(Files.notExists(indexesPath) || !Files.isDirectory(indexesPath)) continue;

            Path objectsPath = assetsPath.resolve("objects");
            if(Files.notExists(objectsPath) || !Files.isDirectory(objectsPath)) continue;

            return assetsPath;
        }
    }

    @Override
    public void start(Stage stage){
        try{
            final Path assetsPath = AssetViewer.getAssetsPath();
            if(assetsPath == null) return;

            final Path indexesPath = assetsPath.resolve("indexes");

            final ChoiceDialog<String> dialog = new ChoiceDialog<>(null, Files.list(indexesPath).map(Path::getFileName).filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json")).map(path -> path.getFileName().toString().substring(0, path.getFileName().toString().lastIndexOf("."))).sorted().collect(Collectors.toList()));
            dialog.setTitle("AssetViewer");
            dialog.setHeaderText("Choose version");

            final String version = dialog.showAndWait().orElseThrow(NullPointerException::new);

            final Path objectsPath = assetsPath.resolve("objects");
            final Path targetPath = Paths.get("out", version);

            final Alert alert = new Alert(Alert.AlertType.INFORMATION, "Copying to " + targetPath.toAbsolutePath().toString() + "...");
            alert.show();

            final JSONObject json = new JSONObject(new String(Files.readAllBytes(indexesPath.resolve(version + ".json")), StandardCharsets.UTF_8));
            AssetViewer.buildStream(JSONObject.class, json.getJSONObject("objects")).forEach(AssetViewer.unsafe(entry -> {
                final String hash = entry.getValue().getString("hash");

                final Path namePath = targetPath.resolve(entry.getKey());
                Files.createDirectories(namePath.getParent());

                final Path objectPath = objectsPath.resolve(hash.substring(0, 2)).resolve(hash);
                Files.copy(objectPath, namePath, StandardCopyOption.REPLACE_EXISTING);

                System.out.println("Copying " + namePath + "...");
            }));

            alert.close();
            new Alert(Alert.AlertType.INFORMATION, "Done!").showAndWait().ifPresent(param -> System.exit(0));
        }catch(IOException | JSONException e){
            e.printStackTrace();
        }
    }

    //Code from https://github.com/ChalkPE/Takoyaki
    public static <T> Stream<Map.Entry<String, T>> buildStream(Class<T> type, JSONObject object){
        return AssetViewer.buildStream(type, object, true);
    }

    //Code from https://github.com/ChalkPE/Takoyaki
    public static <T> Stream<Map.Entry<String, T>> buildStream(Class<T> type, JSONObject object, boolean parallel){
        Map<String, T> map = new HashMap<>();

        if(object != null) for(String i: object.keySet()){
            Object element = object.get(i);
            if(type.isInstance(element)) map.put(i, type.cast(element));
        }

        Stream<Map.Entry<String, T>> stream = map.entrySet().stream();
        return parallel ? stream.parallel() : stream;
    }

    //Code from https://github.com/ChalkPE/Takoyaki
    public interface UnsafeConsumer<T> extends Consumer<T> {
        void acceptUnsafely(T t) throws Exception;

        default void accept(T t){
            try{
                acceptUnsafely(t);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    //Code from https://github.com/ChalkPE/Takoyaki
    public static <T> Consumer<T> unsafe(final UnsafeConsumer<T> consumer){
        return consumer;
    }
}
