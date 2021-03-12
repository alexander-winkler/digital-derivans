package de.ulb.digital.derivans.data;

import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import org.mycore.mets.model.Mets;

import de.ulb.digital.derivans.DigitalDerivansException;
import de.ulb.digital.derivans.model.DescriptiveData;

/**
 * 
 * Builder for descriptive Metadata from METS/MODS
 * 
 * @author u.hartwig
 *
 */
class DescriptiveDataBuilder {

	private String urn = MetadataStore.UNKNOWN;

	private String person = MetadataStore.UNKNOWN;

	private String identifier = MetadataStore.UNKNOWN;

	private String title = MetadataStore.UNKNOWN;

	private String year = MetadataStore.UNKNOWN;

	private String accessCondition = MetadataStore.UNKNOWN;

	private Mets mets;

	private static final Logger LOGGER = LogManager.getLogger(DescriptiveDataBuilder.class);

	/**
	 * Constructor if not METS/MODS available
	 */
	public DescriptiveDataBuilder() {
		this(null);
	}

	/**
	 * 
	 * Constructor with METS/MODS Model
	 * 
	 * @param mets
	 */
	public DescriptiveDataBuilder(Mets mets) {
		this.mets = mets;
	}

	DescriptiveDataBuilder urn() {
		this.urn = getURN();
		return this;
	}

	DescriptiveDataBuilder person() {
		this.person = getPerson();
		return this;
	}

	DescriptiveDataBuilder identifier() throws DigitalDerivansException {
		this.identifier = loadIdentifier();
		return this;
	}

	DescriptiveDataBuilder title() {
		this.title = getTitle();
		return this;
	}

	DescriptiveDataBuilder access() {
		accessCondition = getAccessCondition();
		return this;
	}

	DescriptiveDataBuilder year() {
		year = getYear();
		return this;
	}

	DescriptiveData build() {
		DescriptiveData dd = new DescriptiveData();
		dd.setUrn(urn);
		dd.setPerson(person);
		dd.setIdentifier(identifier);
		dd.setTitle(title);
		dd.setYearPublished(year);
		dd.setLicense(Optional.of(accessCondition));
		LOGGER.debug("build data: '{}'", dd);
		return dd;
	}

	String getPerson() {
		Element mods = getPrimaryMods();
		if (mods != null) {
			Hashtable<String, Element> candidates = new Hashtable<String, Element>();
			List<Element> nameSubtrees = mods.getChildren("name", MetadataStore.NS_MODS);
			
			for(Element nameSubtree: nameSubtrees ) { 
			    if (nameSubtree != null) {
					Element role = nameSubtree.getChild("role", MetadataStore.NS_MODS);
						
					if(role != null) {
						Predicate<Element> codeExists = e -> Objects.nonNull(e.getAttributeValue("authority"));
						List<Element> roleTerms = role.getChildren("roleTerm", MetadataStore.NS_MODS);
						Optional<Element> optRoleTerm = roleTerms.stream().filter(codeExists).findFirst();
						if (optRoleTerm.isPresent()) {
							String rt = optRoleTerm.get().getTextNormalize();
							if(rt.equals("pbl") || rt.equals("aut")) {
								candidates.put(rt, nameSubtree);
							}
						}
					}						
				}
			}
			// we have pbl's and aut in cadidates
			if(candidates.size() > 0){
				Set<Entry<String, Element>> entrySet = candidates.entrySet();
				String publisher = "";

				for(Entry<String, Element> entry : entrySet) {
					Element subtree = entry.getValue();

					// predominant marker "aut"!
					if(entry.getKey().equals("aut")) {
						Element displayElement = subtree.getChild("displayForm", MetadataStore.NS_MODS);
						if (displayElement != null) {
							// fine, we leave here...
							return displayElement.getTextTrim();
						}
						else {
							for (Element child : subtree.getChildren("namePart", MetadataStore.NS_MODS)) {
								String val = child.getAttributeValue("type");
								if (val != null && val.equals("family"))
									// or we leave here...
									return child.getTextTrim();
							}							
						}	
					}
					// we'll collect one or more publisher
					else{
						for (Element child : subtree.getChildren("namePart", MetadataStore.NS_MODS)) {
							String val = child.getAttributeValue("type");
							if (val != null && val.equals("family"))
								publisher += child.getTextTrim() + ", ";
						}							
					}
					
				}
				if (publisher.endsWith(", "))
					publisher = publisher.substring(0, publisher.length()-2);
				return publisher;

			} 
		}
		return MetadataStore.UNKNOWN;
	}

	/**
	 * 
	 * Please note, this information is critical, that it cannot guess a default
	 * value and must be set to something meaningful.
	 * 
	 * If this setup fails due missing metadata, the identifier must be set later
	 * on. Therefore it returns null.
	 * 
	 * @return
	 */
	private String loadIdentifier() throws DigitalDerivansException {
		Element mods = getPrimaryMods();
		if (mods == null) {
			return null;
		}
		Element recordInfo = mods.getChild("recordInfo", MetadataStore.NS_MODS);
		Predicate<Element> sourceExists = e -> Objects.nonNull(e.getAttributeValue("source"));
		List<Element> identifiers = recordInfo.getChildren("recordIdentifier", MetadataStore.NS_MODS);
		Optional<Element> optUrn = identifiers.stream().filter(sourceExists).findFirst();
		if (optUrn.isPresent()) {
			return optUrn.get().getTextTrim();
		}
		throw new DigitalDerivansException("found no valid recordIdentifier");
	}

	String getTitle() {
		Element mods = getPrimaryMods();
		if (mods != null) {
			Element titleInfo = mods.getChild("titleInfo", MetadataStore.NS_MODS);
			return titleInfo.getChild("title", MetadataStore.NS_MODS).getTextNormalize();
		}
		return MetadataStore.UNKNOWN;
	}

	String getURN() {
		Element mods = getPrimaryMods();
		if (mods != null) {
			List<Element> identifiers = mods.getChildren("identifier", MetadataStore.NS_MODS);
			Predicate<Element> typeUrn = e -> e.getAttribute("type").getValue().equals("urn");
			Optional<Element> optUrn = identifiers.stream().filter(typeUrn).findFirst();
			if (optUrn.isPresent()) {
				return optUrn.get().getTextNormalize();
			}
		}
		return MetadataStore.UNKNOWN;
	}

	String getAccessCondition() {
		Element mods = getPrimaryMods();
		if (mods != null) {
			Element cond = mods.getChild("accessCondition", MetadataStore.NS_MODS);
			if (cond != null) {
				return cond.getTextNormalize();
			}
		}
		return MetadataStore.UNKNOWN;
	}

	String getYear() {
		// TODO
		Element mods = getPrimaryMods();
		if (mods != null) {
			PredicateEventTypePublication publicationEvent = new PredicateEventTypePublication();
			Optional<Element> optPubl = mods.getChildren("originInfo", MetadataStore.NS_MODS).stream()
					.filter(publicationEvent).findFirst();
			if (optPubl.isPresent()) {
				Element publ = optPubl.get();
				Element issued = publ.getChild("dateIssued", MetadataStore.NS_MODS);
				if (issued != null) {
					return issued.getTextNormalize();
				}
			}
			// Attribute 'eventType=publication' of node 'publication' is missing
			// so try to find/filter node less consistently
			Element oInfo = mods.getChild("originInfo", MetadataStore.NS_MODS);
			if (oInfo != null) {
				Element issued = oInfo.getChild("dateIssued", MetadataStore.NS_MODS);
				if (issued != null) {
					return issued.getTextNormalize();
				}
			}
		}
		return MetadataStore.UNKNOWN;
	}

	private Element getPrimaryMods() {
		if (mets != null) {
			String dmdId = mets.getLogicalStructMap().getDivContainer().getDmdId();
			return mets.getDmdSecById(dmdId).getMdWrap().getMetadata();
		}
		return null;
	}
}

/**
 * 
 * Predicate for filtering mods:originInfo[@eventType] elements
 * 
 * @author hartwig
 *
 */
class PredicateEventTypePublication implements Predicate<Element> {

	@Override
	public boolean test(Element el) {

		if (el.getAttribute("eventType") != null) {
			String val = el.getAttributeValue("eventType");
			return val.equalsIgnoreCase("publication");
		}
		return false;
	}

}