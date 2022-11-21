package software.amazon.event.ruler.input;

/**
 * @author Aliaksei Bialiauski (abialiauski@solvd.com)
 */
@SuppressWarnings("UseOfConcreteClass")
public interface ByteParser {

  /**
   * @param utf8byte byte that represent in UTF-8 encoding
   * @return processed and parsed Input Character
   */
  InputCharacter parse(byte utf8byte);
}