package net.jgn.cliptext.cmdline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jose on 12/11/16.
 */
public class ConsoleInputLoop {

    private static Pattern SPLIT_WITH_QUOTES = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");
    private String prompt;
    private List<CommandProcessor> commandProcessors;

    public ConsoleInputLoop(String prompt) {
        this.prompt = prompt;
        commandProcessors = new ArrayList<>();
    }

    public void addCommandProcessor(CommandProcessor commandProcessor) {
        this.commandProcessors.add(commandProcessor);
    }

    public void inputLoop() throws IOException {
        if (commandProcessors.isEmpty()) {
            throw new IllegalStateException("There is no command processors!");
        }
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print(prompt);
            String input = console.readLine();
            Optional<CommandProcessor> commandProcessor = commandProcessors.stream()
                    .filter(cp -> cp.matchCommand(input))
                    .findFirst();
            if (commandProcessor.isPresent()) {
                if (!commandProcessor.get().execCommand(inputToCommandList(input))) {
                    break;
                }
            }
        }
    }

    private List<String> inputToCommandList(String input) {
        List<String> list = new ArrayList<String>();
        Matcher m = SPLIT_WITH_QUOTES.matcher(input);
        while (m.find()) {
            list.add(m.group(1).replaceAll("\"", ""));
        }
        return list;
    }
}
