package software.amazon.event.ruler.input;

import java.util.ArrayList;
import java.util.List;

public class HtmlParser {

    /**
     * Constructor
     */
    HtmlParser(){

    }

    public InputCharacter[] parse(String value){
        List<InputCharacter> result = new ArrayList<>(value.length());
        return result.toArray(new InputCharacter[0]);
    }
}
