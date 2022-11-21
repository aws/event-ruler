package software.amazon.event.ruler.input;

import software.amazon.event.ruler.MatchType;

/**
 * @author Aliaksei Bialiauski (abialiauski@solvd.com)
 */
@SuppressWarnings("UseOfConcreteClass")
public interface MatchTypeParser {

  /**
   * @param type Match type
   * @param value string value to parse
   * @return processed and parsed Input Character
   */
  InputCharacter[] parse(MatchType type, String value);
}