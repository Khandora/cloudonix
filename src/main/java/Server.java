import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class Server extends AbstractVerticle {
    private static final String WORDS_FILE_PATH = "src/main/resources/words.txt";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private final List<String> words = new CopyOnWriteArrayList<>();
    private final Set<String> uniqueWords = new CopyOnWriteArraySet<>();

    public static void main(String[] args) {
        Server server = new Server();
        server.startServer();
    }

    private void startServer() {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(this);
    }

    @Override
    public void start(Promise<Void> startPromise) {
        loadWordsFromFile();

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/analyze").handler(this::handleAnalyzeRequest);

        vertx.createHttpServer().requestHandler(router).listen(config().getInteger("http.port", 8080)).onSuccess(server -> startPromise.complete()).onFailure(startPromise::fail);
    }

    @Override
    public void stop() {
        saveWordsToFile();
    }

    private void handleAnalyzeRequest(RoutingContext context) {
        String text = context.body().asJsonObject().getString("text");

        if (text == null) {
            send400InvalidRequestBodyResponse(context.response());
            return;
        }

        String closestByValue = findWordWithClosestCharacterValue(text);
        String closestLexical = findClosestLexicalWord(text);
        addWord(text);

        String responseBuilder = "{\"value\": \"" + closestByValue + "\", \"lexical\": \"" + closestLexical + "\"}";
        context.response().putHeader("Content-Type", CONTENT_TYPE_JSON).end(responseBuilder);
    }

    private void addWord(String newWord) {
        if (uniqueWords.add(newWord)) {
            words.add(newWord);
        }
        words.sort(String::compareTo);
    }

    private String findWordWithClosestCharacterValue(String targetWord) {
        int targetValue = calculateCharacterValue(targetWord);
        int minDifference = Integer.MAX_VALUE;
        String wordWithClosestCharacterValue = null;

        for (String word : words) {
            if (word.equals(targetWord)) {
                continue;
            }

            int wordValue = calculateCharacterValue(word);
            int difference = Math.abs(wordValue - targetValue);

            if (difference < minDifference) {
                minDifference = difference;
                wordWithClosestCharacterValue = word;
            }
        }

        return wordWithClosestCharacterValue;
    }

    private int calculateCharacterValue(String word) {
        int value = 0;

        for (char c : word.toCharArray()) {
            value += Character.toLowerCase(c) - 'a' + 1;
        }

        return value;
    }

    private String findClosestLexicalWord(String targetWord) {
        int left = 0;
        int right = words.size() - 1;
        int minDistance = Integer.MAX_VALUE;
        String closestWord = null;

        while (left <= right) {
            int mid = (left + right) / 2;
            String currentWord = words.get(mid);
            int distance = currentWord.compareTo(targetWord);

            if (distance == 0) {
                closestWord = currentWord;
                break;
            } else if (distance < 0) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }

            if (Math.abs(distance) < minDistance) {
                minDistance = Math.abs(distance);
                closestWord = currentWord;
            }
        }

        return closestWord;
    }

    private void loadWordsFromFile() {
        File file = new File(WORDS_FILE_PATH);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                words.addAll(reader.lines().toList());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                if (file.createNewFile()) {
                    System.out.println("file created " + file.getPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveWordsToFile() {
        File file = new File(WORDS_FILE_PATH);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            for (String word : words) {
                writer.write(word);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void send400InvalidRequestBodyResponse(HttpServerResponse response) {
        response.setStatusCode(400).end("Invalid request body");
    }
}