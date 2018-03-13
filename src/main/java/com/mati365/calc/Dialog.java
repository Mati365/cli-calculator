/* file name  : src/main/java/com/mati365/calc/Dialog.java
 * authors    : Mateusz Bagiński (cziken58@gmail.com)
 * created    : ndz 11 mar 20:44:24 2018
 * copyright  : MIT
 */
package com.mati365.calc;

import java.io.IOException;
import java.util.Arrays;
import java.util.Stack;

import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.gui2.*;

import org.apache.commons.lang3.math.NumberUtils;

import com.mati365.calc.Resources;

/** 
 * Evaluates expression 
 * 
 * @author Mateusz Bagiński (cziken58@gmail.com)
 */
class Arithmetic {
    private enum Error {
        ZERO_DIVIDE("zero_divide"),
        UNCLOSED_CURLY("unclosed_curly"),
        SYNTAX_ERROR("syntax_error");
        
        private final String message;
        
        private Error(@NotNull String message) { 
            this.message = message;         
        }
        
        public String getMessage() { 
            return Resources.Translations.getString(message); 
        }
    }

    private enum Operator {
        LEFT_BRACKET(0, '(', null),  
        RIGHT_BRACKET(1, ')', null),
        PLUS(1, '+', (b, a) -> a + b), 
        MINUS(1, '-', (b, a) -> a - b), 
        MUL(2, '*', (b, a) -> a * b), 
        DIV(2, '/', (b, a) -> a / b), 
        MOD(2, '%', (b, a) -> a % b);
        
        private final int value;
        private final char character;
        private final BiFunction<Float, Float, Float> action;

        private Operator(int value, char character, BiFunction<Float, Float, Float> action) {
            this.value = value;
            this.character = character;
            this.action = action;
        }
        
        public BiFunction<Float, Float, Float> getAction() { return action; }
        public int getValue() { return value; }
        public char getChar() { return character; }
        
        /** 
         * Returns all arithmetic symbols, used in regex match tokens 
         * 
         * @return 
         */
        public static final String allCharacters() {
            return Arrays
                .stream(Operator.values())
                .map(item -> String.valueOf(item.getChar()))
                .reduce("", (acc, item) -> acc + item);
        }

        /**
         * Compares two operators by value
         *
         * @param op 
         * @return True if current operator is bigger than provied
         */
        public boolean isBigger(@NotNull Operator op) {
            return this.value <= op.value;
        }

        /** 
         * Finds operator by its charcode 
         * 
         * @param operator Operator char code
         * @return Operator
         */
        public static Operator find(char operator) {
            for (Operator op : Operator.values()) {
                if (op.character == operator)
                    return op;
            }
            return null;
        }
    }
    
    /** 
     * Enumerator arithmetic exception 
     * 
     * @author Mateusz Bagiński (cziken58@gmail.com)
     */
    public static class ArithmeticException extends Exception {
        private static final long serialVersionUID = 1L;
        private Error code = null;

        public ArithmeticException(@NotNull Error code) {
            this.code = code;
        }

        public Error getCode() { return this.code; }
        
        @Override
        public String getMessage() { return this.code.getMessage(); }
    }

    /** 
     * Splits math string into parts 
     * 
     * @param expression 
     * @return 
     */
    private static final ArrayList<String> tokenize(@NotNull String expression) {
        ArrayList<String> tokens = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(
                expression
                    .replaceAll("\\s+","")            
                    .replaceAll(",", "."), 
                Operator.allCharacters(), 
                true);
        
        while (st.hasMoreTokens()) 
            tokens.add(st.nextToken());
        
        return tokens;
    }

    /** 
     * Converts expression to RPN  
     * 
     * @param expression 
     * @return RPN expression value
     * @throws ArithmeticExcpetion  
     */
    private static final String SYNTAX_ERROR_MAGIC = "<SYNTAX ERROR>";

    public static final String toRPN(@NotNull String expression) throws ArithmeticException { 
        Stack<Operator> stack = new Stack<Operator>();
        String rpn = Arithmetic
            .tokenize(expression)
            .stream()
            .reduce("", (acc, item) -> {
                // if number pass to exit
                if (NumberUtils.isCreatable(item))
                    return acc + " " + item;
                
                Operator op = Operator.find(item.charAt(0));
                if (op == null)
                    return Arithmetic.SYNTAX_ERROR_MAGIC;
                
                // if right bracket force pop until left bracket
                boolean dropToBracket = op.equals(Operator.RIGHT_BRACKET);
                
                // if found right bracket drop until left 
                if (!stack.empty() && !op.equals(Operator.LEFT_BRACKET)) {
                    while (true) {
                        if (stack.empty()) {
                            if (dropToBracket)
                                return Arithmetic.SYNTAX_ERROR_MAGIC;
                            break;
                        }

                        Operator head = stack.peek();
                        boolean leftBracket = head.equals(Operator.LEFT_BRACKET);
                        
                        if (dropToBracket ? !leftBracket : op.isBigger(head)) {
                            acc += " " + head.getChar();
                            stack.pop();
                        } else {
                            if (dropToBracket && leftBracket) {
                                stack.pop(); 
                                return acc;
                            }
                            break;
                        }
                    }
                }

                if (!dropToBracket)
                    stack.push(op);
                return acc;
            });
        
        // add rest of operators
        StringBuilder builder = new StringBuilder(rpn); 
        while(!stack.empty())
            builder.append(" " + stack.pop().getChar());
        rpn = builder.toString();
        
        // if contains curly bracket, its exception
        if (rpn.contains(String.valueOf(Operator.LEFT_BRACKET.getChar())))
            throw new ArithmeticException(Error.UNCLOSED_CURLY);
        
        // workaround to fix Lambda throw exceptions
        else if (rpn.contains(Arithmetic.SYNTAX_ERROR_MAGIC))
            throw new ArithmeticException(Error.SYNTAX_ERROR);

        return rpn;
    }

    /** 
     * Evaluates expression 
     * 
     * @param expression Expression in string format
     * @return Expression numeric value
     * @throws ArithmeticExcpetion  
     */
    public static final float parseExpression(@NotNull String expression) throws ArithmeticException {
        Stack<Float> stack = new Stack<Float>(); 
        Optional<String> brokenOperand = Arrays.asList(
                Arithmetic
                    .toRPN(expression)
                    .split(" "))
            .stream()

            .filter((item) -> {
                if (item.isEmpty())
                    return false;

                Operator op = Operator.find(item.charAt(0));

                if (op != null) {
                    if (stack.size() < 2)
                        return true;
        
                    Float val = op
                        .getAction().apply(
                                stack.pop(),
                                stack.pop());
                    stack.push(val);
                } else
                    stack.push(Float.parseFloat(item));

                return false;
            })
            .findFirst();
        
        if (stack.empty())
            throw new ArithmeticException(Error.SYNTAX_ERROR);

        return stack.pop(); 
    }
}

/** 
 * Handle input change using lambda expression  
 * 
 * @author Mateusz Bagiński (cziken58@gmail.com)
 */
@FunctionalInterface
interface TextEditListener {
    void handleEdit(String text);
}

class LiveTextBox extends TextBox {
    private TextEditListener listener;

    public LiveTextBox(TerminalSize size, TextEditListener listener) {
        super(size);
        this.listener = listener;
    }

    public Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
        Interactable.Result result = super.handleKeyStroke(keyStroke);
        this.listener.handleEdit(this.getText()); 
        return result;
    }
} 

/** 
 * Class that contains base app container 
 *  
 * @author Mateusz Bagiński (cziken58@gmail.com)
 */
class CalculatorWindow {
    private Button exit = null;

    private Panel panel = new Panel();
    private Label label = new Label(Resources.Translations.getString("expression_result"))
            .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning))
            .addStyle(SGR.BOLD);
    
    public CalculatorWindow() {
        this.panel
            .setLayoutManager(new LinearLayout(Direction.VERTICAL))
            .addComponent(
                    new Label(
                        Resources.Translations.getString("expression_label")));
        
        LiveTextBox input = new LiveTextBox(
                new TerminalSize(48, 5),
                this::handleExpressionUpdate);

        input.addTo(this.panel);

        this.label.addTo(this.panel);
        new EmptySpace(new TerminalSize(0,1))
            .addTo(this.panel);
        
        this.exit = new Button(Resources.Translations.getString("exit"));
        this.exit
            .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.End))
            .addTo(this.panel);
    }
    
    /** 
     * Calculates math sequence 
     * 
     * @param str
     */
    private void handleExpressionUpdate(@NotNull String str) { 
        try {
            String output = (
                str.isEmpty()
                    ? Resources.Translations.getString("empty_result")
                    : Resources.Translations.getString("expression_result", Arithmetic.parseExpression(str))
            );
            this.label.setText(output);

        } catch(Arithmetic.ArithmeticException e) {
            this.label.setText(e.getMessage());
        }    
    }

    /** 
     * Create base window handle 
     * 
     * @return BasicWindow instance 
     */
    public BasicWindow getDefaultWindow() { 
        BasicWindow window = new BasicWindow(
                Resources.Translations.getString("app_name"));

        window.setHints(Arrays.asList(Window.Hint.CENTERED));
        window.setComponent(this.panel);
        
        this.exit.addListener(new Button.Listener() {
            @Override
            public void onTriggered(Button button) {
                window.close();
            }
        });
        return window;
    }
}


/** 
 * Whole app container
 *
 * @author Mateusz Bagiński (cziken58@gmail.com)
 */
class CalculatorTerminal {
    private TerminalScreen screen;
    private MultiWindowTextGUI gui;

    public CalculatorTerminal(@NotNull TextColor backgroundColor) throws IOException  { 
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        
        this.screen = new TerminalScreen(terminal);
        this.screen.startScreen();
     
        this.gui = new MultiWindowTextGUI(
                this.screen, 
                new DefaultWindowManager(), 
                new EmptySpace(backgroundColor)); 
    }

    public void attachWindow(@NotNull BasicWindow window) {    
        gui.addWindowAndWait(window);
    }    
}

public class Dialog {
    public static void main(String[] args) {
        try {
            new CalculatorTerminal(TextColor.ANSI.BLUE)
                .attachWindow(new CalculatorWindow().getDefaultWindow());
        } catch(IOException e) {
            System.err.println("Something wrong happened during loading window!\nAbort to start program.");
        }
    }
}
