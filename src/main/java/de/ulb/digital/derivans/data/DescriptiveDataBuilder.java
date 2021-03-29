package de.ulb.digital.derivans.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import org.mycore.mets.model.Mets;
import org.mycore.mets.model.sections.DmdSec;
import org.mycore.mets.model.struct.LogicalStructMap;

import de.ulb.digital.derivans.DigitalDerivansException;
import de.ulb.digital.derivans.model.DescriptiveData;

import static de.ulb.digital.derivans.data.MetadataStore.*; 

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
	
	private MetadataHandler handler;

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
		dd.setIdentifier(identifier);
		dd.setTitle(title);
		dd.setYearPublished(year);
		dd.setPerson(person);
		dd.setLicense(Optional.of(accessCondition));
		LOGGER.debug("build data: '{}'", dd);
		return dd;
	}

	String getPerson() {
		Element mods = getPrimaryMods();
		if (mods != null) {
			List<Element> nameSubtrees = mods.getChildren("name", NS_MODS);

			// collect proper name relations
			Map<MARCRelator, List<Element>> properRelations = getDesiredRelations(nameSubtrees);
			if(properRelations.isEmpty()) {
				LOGGER.warn("found no proper related persons!");
				return MetadataStore.UNKNOWN;
			}

			// assume we have pbl's or aut's candidates
			if(properRelations.containsKey(MARCRelator.AUTHOR)) {
				return getSomeName(properRelations.get(MARCRelator.AUTHOR));
			} else if(properRelations.containsKey(MARCRelator.PUBLISHER)) {
				return getSomeName(properRelations.get(MARCRelator.PUBLISHER));
			}

		}
		return MetadataStore.UNKNOWN;
	}

	/**
	 * 
	 * Get some name for first entry
	 * 
	 * @param list
	 * @return
	 */
	private String getSomeName(List<Element> list) {
		for(Element e : list) {
			for(Element f : e.getChildren("displayForm", NS_MODS)) {
				return f.getTextNormalize();
			}
			for(Element f : e.getChildren("namePart", NS_MODS)) {
				if("family".equals(f.getAttributeValue("type"))) {
					return f.getTextNormalize();
				}
			}
		}
		return MetadataStore.UNKNOWN;
	}

	private Map<MARCRelator, List<Element>> getDesiredRelations(List<Element> nameSubtrees) {
		Map<MARCRelator, List<Element>> map = new TreeMap<>();
		for (Element e : nameSubtrees) {
			for (Element f : e.getChildren("role", NS_MODS)) {
				for (Element g : f.getChildren("roleTerm", NS_MODS)) {
					if ("code".equals(g.getAttributeValue("type"))) {
						String code = g.getTextNormalize();
						MARCRelator rel = MARCRelator.forCode(code);
						switch (rel) {
						case AUTHOR:
						case PUBLISHER:
							LOGGER.debug("map '{}' as person", rel);
							List<Element> currList = new ArrayList<>();
							currList.add(e);
							map.merge(rel, currList, (prev, curr) -> {
								prev.addAll(curr);
								return prev;
							});
							break;
						default:
							LOGGER.debug("dont map '{}' as person", rel);
						}
					}
				}
			}
		}
		return map;
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
		Element recordInfo = mods.getChild("recordInfo", NS_MODS);
		Predicate<Element> sourceExists = e -> Objects.nonNull(e.getAttributeValue("source"));
		List<Element> identifiers = recordInfo.getChildren("recordIdentifier", NS_MODS);
		Optional<Element> optUrn = identifiers.stream().filter(sourceExists).findFirst();
		if (optUrn.isPresent()) {
			return optUrn.get().getTextTrim();
		}
		throw new DigitalDerivansException("found no valid recordIdentifier");
	}

	String getTitle() {
		Element mods = getPrimaryMods();
		if (mods != null) {
			Element titleInfo = mods.getChild("titleInfo", NS_MODS);
			if (titleInfo != null) {
				return titleInfo.getChild("title", NS_MODS).getTextNormalize();
			}
			// take care of host title (kitodo2)
			// TODO: do some handler stuff
			// currently, mods:relatedItem is *not* handled by mets-model
		}
		return MetadataStore.UNKNOWN;
	}

	String getURN() {
		Element mods = getPrimaryMods();
		if (mods != null) {
			List<Element> identifiers = mods.getChildren("identifier", NS_MODS);
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
			Element cond = mods.getChild("accessCondition", NS_MODS);
			if (cond != null) {
				return cond.getTextNormalize();
			}
		}
		return MetadataStore.UNKNOWN;
	}

	String getYear() {
		Element mods = getPrimaryMods();
		if (mods != null) {
			PredicateEventTypePublication publicationEvent = new PredicateEventTypePublication();
			Optional<Element> optPubl = mods.getChildren("originInfo", NS_MODS).stream()
					.filter(publicationEvent).findFirst();
			if (optPubl.isPresent()) {
				Element publ = optPubl.get();
				Element issued = publ.getChild("dateIssued", NS_MODS);
				if (issued != null) {
					return issued.getTextNormalize();
				}
			}
			// Attribute 'eventType=publication' of node 'publication' is missing
			// so try to find/filter node less consistently
			Element oInfo = mods.getChild("originInfo", NS_MODS);
			if (oInfo != null) {
				Element issued = oInfo.getChild("dateIssued", NS_MODS);
				if (issued != null) {
					return issued.getTextNormalize();
				}
			}
		}
		return MetadataStore.UNKNOWN;
	}

	private Element getPrimaryMods() {
		if (mets != null) {
			String dmdId = getLinkFromMonography(mets.getLogicalStructMap());
			DmdSec dmd = mets.getDmdSecById(dmdId);
			if (dmd != null) {
				return dmd.getMdWrap().getMetadata();
			} else {
				// kitodo2 multivolume work
				String firstDmdId = getLinkIDsFromFirstVolume(mets.getLogicalStructMap());
				if(firstDmdId == null) {
					return null;
				}
				dmd = mets.getDmdSecById(firstDmdId);
				if(dmd != null) {
					return dmd.getMdWrap().getMetadata();
				}
			}
		}
		return null;
	}
	
	private static String getLinkFromMonography(LogicalStructMap logMap) {
		return logMap.getDivContainer().getDmdId();
	}
	
	private String getLinkIDsFromFirstVolume(LogicalStructMap logMap) {
		return this.handler.requestDMDSubDivIDs("DMDID");
	}

	public void setHandler(MetadataHandler handler) {
		this.handler = handler;
		
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

/**
 * 
 * Map MARC relator codes with enum
 * 
 * @author u.hartwig
 *
 */
enum MARCRelator {

	AUTHOR("aut"), 
	ASSIGNED_NAME("asn"), 
	CONTRIBUTOR("ctb"), 
	OTHER("oth"), 
	PUBLISHER("pbl"), 
	PRINTER("prt"),
	UNKNOWN("n.a.");

	private String code;

	private MARCRelator(String code) {
		this.code = code;
	}

	public static MARCRelator forCode(String code) {
		for (MARCRelator e : values()) {
			if (e.code.equals(code)) {
				return e;
			}
		}
		return UNKNOWN;
	}
}
