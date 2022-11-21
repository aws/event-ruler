package software.amazon.event.ruler.input;

import software.amazon.event.ruler.MatchType;

/**
 * @author Aliaksei Bialiauski (abialiauski@solvd.com)
 */
@SuppressWarnings("UseOfConcreteClass")
public interface StringValueParser extends MatchTypeParser {

  default InputCharacter[] parse(final String value) {
    return this.parse(MatchType.NO, value);
  }
}