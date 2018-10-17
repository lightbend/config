package com.typesafe.config.parser

import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.impl.Parseable
import java.io.File
import java.io.Reader

/**
 * Factory for creating {@link
 * com.typesafe.config.parser.ConfigDocument} instances.
 */
object ConfigDocumentFactory {

    /**
     * Parses a Reader into a ConfigDocument instance.
     *
     * @param reader
     *       the reader to parse
     * @param options
     *       parse options to control how the reader is interpreted
     * @return the parsed configuration
     * @throws com.typesafe.config.ConfigException on IO or parse errors
     */
    def parseReader(
        reader: Reader,
        options: ConfigParseOptions): ConfigDocument =
        Parseable.newReader(reader, options).parseConfigDocument

    /**
     * Parses a reader into a Config instance as with
     * {@link #parseReader(Reader,ConfigParseOptions)} but always uses the
     * default parse options.
     *
     * @param reader
     *       the reader to parse
     * @return the parsed configuration
     * @throws com.typesafe.config.ConfigException on IO or parse errors
     */
    def parseReader(reader: Reader): ConfigDocument =
        parseReader(reader, ConfigParseOptions.defaults)

    /**
     * Parses a file into a ConfigDocument instance.
     *
     * @param file
     *       the file to parse
     * @param options
     *       parse options to control how the file is interpreted
     * @return the parsed configuration
     * @throws com.typesafe.config.ConfigException on IO or parse errors
     */
    def parseFile(file: File, options: ConfigParseOptions): ConfigDocument =
        Parseable.newFile(file, options).parseConfigDocument

    /**
     * Parses a file into a ConfigDocument instance as with
     * {@link #parseFile(File,ConfigParseOptions)} but always uses the
     * default parse options.
     *
     * @param file
     *       the file to parse
     * @return the parsed configuration
     * @throws com.typesafe.config.ConfigException on IO or parse errors
     */
    def parseFile(file: File): ConfigDocument =
        parseFile(file, ConfigParseOptions.defaults)

    /**
     * Parses a string which should be valid HOCON or JSON.
     *
     * @param s string to parse
     * @param options parse options
     * @return the parsed configuration
     */
    def parseString(s: String, options: ConfigParseOptions): ConfigDocument =
        Parseable.newString(s, options).parseConfigDocument

    /**
     * Parses a string (which should be valid HOCON or JSON). Uses the
     * default parse options.
     *
     * @param s string to parse
     * @return the parsed configuration
     */
    def parseString(s: String): ConfigDocument =
        parseString(s, ConfigParseOptions.defaults)
}
