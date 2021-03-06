/**
 * This file is part of CERMINE project.
 * Copyright (c) 2011-2013 ICM-UW
 *
 * CERMINE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CERMINE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with CERMINE. If not, see <http://www.gnu.org/licenses/>.
 */

package pl.edu.icm.cermine.metadata.affiliation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.*;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import pl.edu.icm.cermine.exception.AnalysisException;
import pl.edu.icm.cermine.exception.TransformationException;
import pl.edu.icm.cermine.metadata.affiliation.tools.AffiliationCRFTokenClassifier;
import pl.edu.icm.cermine.metadata.affiliation.tools.AffiliationFeatureExtractor;
import pl.edu.icm.cermine.metadata.affiliation.tools.AffiliationTokenizer;
import pl.edu.icm.cermine.metadata.model.AffiliationLabel;
import pl.edu.icm.cermine.metadata.model.DocumentAffiliation;
import pl.edu.icm.cermine.metadata.transformers.DocumentMetadataToNLMElementConverter;
import pl.edu.icm.cermine.parsing.model.Token;
import pl.edu.icm.cermine.parsing.tools.ParsableStringParser;

/**
 * CRF-based Affiliation parser. 
 * Processes an instance of DocumentAffiliation by generating and tagging its tokens.
 * 
 * @author Bartosz Tarnawski
 */
public class CRFAffiliationParser implements ParsableStringParser<DocumentAffiliation> {

	private AffiliationTokenizer tokenizer = null;
	private AffiliationFeatureExtractor featureExtractor = null;
	private AffiliationCRFTokenClassifier classifier = null;
	
	private static final String DEFAULT_MODEL_FILE = 
			"/pl/edu/icm/cermine/metadata/affiliation/acrf-affiliations-pubmed.ser.gz";
	private static final String DEFAULT_COMMON_WORDS_FILE = 
			"/pl/edu/icm/cermine/metadata/affiliation/common-words-affiliations-pubmed.txt";

	
	private List<String> loadWords(String wordsFileName) throws AnalysisException {
		List<String> commonWords = new ArrayList<String>();
		InputStream is = getClass().getResourceAsStream(wordsFileName);
		if (is == null) {
			throw new AnalysisException("Resource not found: " + wordsFileName);
		}
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		try {
			String line;
			while ((line = in.readLine()) != null) {
				commonWords.add(line);
			}
		} catch (IOException readException) {
			throw new AnalysisException("An exception occured when the common word list "
					+ wordsFileName + " was being read: " + readException);
		} finally {
			try {
				in.close();
			} catch (IOException closeException) {
				throw new AnalysisException("An exception occured when the stream was being " +
						"closed: " + closeException);
			}
		}
		return commonWords;
	}
	
	/**
	 * @param wordsFileName the name of the package resource to be used as the common words list
	 * @param acrfFileName the name of the package resource to be used as the ACRF model
	 * @throws AnalysisException
	 */
	public CRFAffiliationParser(String wordsFileName, String acrfFileName) throws AnalysisException {
		List<String> commonWords = loadWords(wordsFileName);
		tokenizer = new AffiliationTokenizer();
		featureExtractor = new AffiliationFeatureExtractor(commonWords);
		classifier = new AffiliationCRFTokenClassifier(
				getClass().getResourceAsStream(acrfFileName));
	}
	
	public CRFAffiliationParser() throws AnalysisException {
		this(DEFAULT_COMMON_WORDS_FILE, DEFAULT_MODEL_FILE);
	}

	/**
	 * Sets the token list of the affiliation so that their labels
	 * determine the tagging of its text content. 
	 * 
	 * @param affiliation
	 * @throws AnalysisException
	 */
    @Override
	public void parse(DocumentAffiliation affiliation) throws AnalysisException {
		affiliation.setTokens(tokenizer.tokenize(affiliation.getRawText()));
        for (Token<AffiliationLabel> t : affiliation.getTokens()) {
            t.setLabel(AffiliationLabel.TEXT);
        }
		featureExtractor.calculateFeatures(affiliation);
		classifier.classify(affiliation.getTokens());
        affiliation.mergeTokens();
	}

	/**
	 * @param affiliationString string representation of the affiliation to parse
	 * @return XML Element with the tagged affiliation in NLM format
	 * @throws AnalysisException
	 * @throws TransformationException 
	 */
    @Override
	public Element parse(String affiliationString) throws AnalysisException,
	TransformationException {
		DocumentAffiliation aff = new DocumentAffiliation(affiliationString);
		parse(aff);
        DocumentMetadataToNLMElementConverter converter = new DocumentMetadataToNLMElementConverter();
        return converter.convertAffiliation(aff);
	}
    
    public static void main(String[] args) throws ParseException, AnalysisException, TransformationException {
        Options options = new Options();
        options.addOption("affiliation", true, "reference text");
        
        CommandLineParser clParser = new GnuParser();
        CommandLine line = clParser.parse(options, args);
        String affiliationText = line.getOptionValue("affiliation");
        
        if (affiliationText == null) {
       		System.err.println("Usage: CRFAffiliationParser -affiliation <affiliation text>\n\n"
                             + "Tool for extracting metadata from affiliation strings.\n\n"
                             + "Arguments:\n"
                             + "  -affiliation            the text of the affiliation\n");
            System.exit(1);
        }
        
        CRFAffiliationParser parser = new CRFAffiliationParser();
        Element affiliation = parser.parse(affiliationText);
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        System.out.println(outputter.outputString(affiliation));
    }

}
