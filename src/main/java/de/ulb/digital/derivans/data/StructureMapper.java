package de.ulb.digital.derivans.data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.mets.model.Mets;
import org.mycore.mets.model.struct.LogicalDiv;
import org.mycore.mets.model.struct.LogicalStructMap;
import org.mycore.mets.model.struct.PhysicalSubDiv;
import org.mycore.mets.model.struct.SmLink;
import org.mycore.mets.model.struct.StructLink;

import de.ulb.digital.derivans.DigitalDerivansException;
import de.ulb.digital.derivans.model.DigitalStructureTree;

/**
 * 
 * 
 * Create Tree-like structure for PDF-Outline
 * 
 * @author hartwig
 *
 */
class StructureMapper {

	private static final Logger LOGGER = LogManager.getLogger(StructureMapper.class);

	// special case, where the complete physical section is
	// linked to the top-most logical entity
	public static final String STRUCT_PHYSICAL_ROOT = "physroot";

	// mark missing data
	public static final String UNSET = "n.a.";

	private Mets mets;

	private String title;

	private boolean renderPlainLeafes;

	public StructureMapper(Mets mets, String title) {
		this(mets, title, true);
	}

	/**
	 * 
	 * Create Instance
	 * 
	 * Set renderPlainLeafes = false to disable rendering 
	 * of plain page elements of a print.
	 * 
	 * @param mets
	 * @param title
	 * @param renderPlainLeafes
	 */
	public StructureMapper(Mets mets, String title, boolean renderPlainLeafes) {
		this.mets = mets;
		this.title = title;
		this.renderPlainLeafes = renderPlainLeafes;
	}

	public DigitalStructureTree build() throws DigitalDerivansException {
		if (this.mets != null) {
			LogicalStructMap lsm = this.mets.getLogicalStructMap();
			if (lsm == null) {
				String msg = "mets is missing logical StructMap!";
				LOGGER.error(msg);
				throw new DigitalDerivansException(msg);
			}
			LogicalDiv logDiv = lsm.getDivContainer();
			DigitalStructureTree theRoot = new DigitalStructureTree();
			String label = logDiv.getLabel();
			if (label == null) {
				label = logDiv.getOrderLabel();
				if (label == null) {
					label = this.title;
				}
			}
			theRoot.setLabel(label);
			theRoot.setPage(1);
			var typedKids = logDiv.getChildren().stream().filter(div -> div.getType() != null).collect(Collectors.toList());
			for (LogicalDiv logicalChild : typedKids) {
				// hack around bug: not only div children are respected
				// to avoid empty entries from TextNodes
				if (logicalChild.getType() != null) {
					DigitalStructureTree subTree = new DigitalStructureTree();
					theRoot.addSubStructure(subTree);
					extendStructure(subTree, logicalChild);
				}
			}

			// review
			clearStructure(theRoot);

			return theRoot;
		} else {
			LOGGER.warn("no mets avaiable");
		}
		return null;
	}


	/**
	 * 
	 * Handle possible invalid links from logicalContainers to physicalSequences for
	 * all descendant structure nodes recursively
	 * 
	 * @param tree
	 */
	private void clearStructure(DigitalStructureTree tree) {
		if (tree.hasSubstructures()) {
			for (DigitalStructureTree subTree : tree.getSubstructures()) {
				if (subTree.getPage() == -1) {
					boolean isRemoved = tree.removeSubStructure(subTree);
					LOGGER.warn("Droped invalid subStructure '{}':{}", subTree, isRemoved);
				}
				clearStructure(subTree);
			}
		}
	}

	void extendStructure(DigitalStructureTree currentNode, LogicalDiv currentLogicalDiv) 
		throws DigitalDerivansException {

		// set required data for current node
		currentNode.setLabel(getLabel(currentLogicalDiv));
		MapLeafs mapedLeafs = mapLogicalDivToPhysicalSequence(currentLogicalDiv);
		currentNode.setPage(mapedLeafs.order);

		// handle current leafs (= just pages)
		// if any exists and if this is required
		if (this.renderPlainLeafes) {
			for(var leaf : mapedLeafs.leafs) {
				String leafLabel = StructureMapper.getLabel(leaf);
				var leafStruct = new DigitalStructureTree(leaf.getOrder(), leafLabel);
				currentNode.addSubStructure(leafStruct);
			}
		}

		// iterate further down the structure tree
		if (currentLogicalDiv.getChildren() != null) {
			for (LogicalDiv child : currentLogicalDiv.getChildren()) {
				DigitalStructureTree subTree = new DigitalStructureTree();
				currentNode.addSubStructure(subTree);
				extendStructure(subTree, child);
			}
		}
	}

	/**
	 * 
	 * Guess name/label from logical container
	 * 
	 * @param logical
	 * @return
	 */
	private static String getLabel(LogicalDiv logical) {
		String label = logical.getLabel();
		if (label != null && ! label.isBlank()) {
			return label;
		}
		String orderLabel = logical.getOrderLabel();
		if (orderLabel != null && ! orderLabel.isBlank()) {
			return orderLabel;
		}
		String logicalType = logical.getType();
		return mapLogicalType(logicalType);
	}

	/**
	 * 
	 * Guess name/label for single page from 
	 * physical section
	 * 
	 * @param logical
	 * @return
	 */
	private static String getLabel(PhysicalSubDiv physical) throws DigitalDerivansException {
		String label = physical.getLabel();
		if (label != null && ! label.isBlank()) {
			return label;
		}
		String orderLabel = physical.getOrderLabel();
		if (orderLabel != null && ! orderLabel.isBlank()) {
			return orderLabel;
		}
		throw new DigitalDerivansException("No valid labelling for page '"+physical.getId()+"'");
	}

	/**
	 * 
	 * Try to map a logical structure to the order of the corresponding physical
	 * structure. This way the start page of a logical structure
	 * 
	 * @param ld
	 * @return pageNumber (default: 1)
	 */
	private MapLeafs mapLogicalDivToPhysicalSequence(LogicalDiv ld) throws DigitalDerivansException {
		String logId = ld.getId();
		StructLink structLink = mets.getStructLink();
		List<SmLink> smLinksTo = structLink.getSmLinkByFrom(logId);
		if (!smLinksTo.isEmpty()) {

			// according to latest (2022-05-05) requirements
			// iterate over *_all_* physical containers linked 
			// from this logical container
			try {

				// try to get the link to the target physical section
				String physId = smLinksTo.get(0).getTo();

				// handle the special semantics root container
				// this is mapped immediately to page "1"
				if (physId.equalsIgnoreCase(STRUCT_PHYSICAL_ROOT)) {
					var rootLeaf = new MapLeafs();
					rootLeaf.order = 1;
					return rootLeaf;
				}
			
				// request valid link from logical to physical container
				PhysicalSubDiv physDiv = mets.getPhysicalStructMap().getDivContainer().get(physId);
				if (physDiv == null) {
					throw new DigitalDerivansException("Invalid physical struct '"+physId+"'!");
				}
				Integer order = physDiv.getOrder();
				if (order == null) {
					throw new DigitalDerivansException("no order for "+logId);
				}
				var mapLeafs = new MapLeafs();
				mapLeafs.order = order;

				// collect links for every page otherwise
				// but *ONLY* if this is not one of the top-most containers!
				// this is Kitodo2 related, where there is no such thing 
				// as a simple "physRoot" linking but each physical page is also linked
				// to the monograph/F-stage, too
				if (! isTopLogicalContainer(ld)) {
					mapLeafs.leafs = smLinksTo.stream()
					.map(smLink -> mets.getPhysicalStructMap().getDivContainer().get(smLink.getTo()))
					.collect(Collectors.toList());
				}

				return mapLeafs;
			} catch (DigitalDerivansException e) {
				throw new DigitalDerivansException("LogId '"+logId+"' : "+e.getMessage());
			}
		}
		LOGGER.warn("No phys struct maps logical struct '{}' - default to 'null'!", logId);
		String logStr = String.format("%s@%s(%s)", logId, ld.getType(), ld.getLabel());
		throw new DigitalDerivansException("No physical struct linked from '"+logStr+"'!");
	}

	private static boolean isTopLogicalContainer(LogicalDiv logDiv) {
		String theType = logDiv.getType();
		var tops = List.of("volume", "monograph");
		return tops.stream().anyMatch(t -> t.equals(theType));
	}

	/**
	 * 
	 * see: http://dfg-viewer.de/strukturdatenset/
	 * 
	 * @param logicalType
	 * @return
	 */
	private static String mapLogicalType(String logicalType) {
		switch (logicalType) {
		case "cover_front":
			return "Vorderdeckel";
		case "cover_back":
			return "Rückdeckel";
		case "title_page":
			return "Titelblatt";
		case "preface":
			return "Vorwort";
		case "dedication":
			return "Widmung";
		case "illustration":
			return "Illustration";
		case "image":
			return "Bild";
		case "table":
			return "Tabelle";
		case "contents":
			return "Inhaltsverzeichnis";
		case "engraved_titlepage":
			return "Kupfertitel";
		case "map":
			return "Karte";
		case "imprint":
			return "Impressum";
		case "corrigenda":
			return "Errata";
		case "section":
			return "Abschnitt";
		case "provenance":
			return "Besitznachweis";
		case "bookplate":
			return "Exlibris";
		case "entry":
			return "Eintrag";
		case "printers_mark":
			return "Druckermarke";
		case "chapter":
			return "Kapitel";
		case "index":
			return "Register";
		// important if volume misses "LABEL"
		case "volume":
			return "Band";
		default:
			LOGGER.error("no mapping for logical type: '{}'", logicalType);
			return null;
		}
	}
}

/**
 * Internal Data Container Clazz
 */
class MapLeafs {
	Integer order = 1;
	List<PhysicalSubDiv> leafs = new ArrayList<>();

	@Override
	public String toString() {
		return String.format("p%04d (%d leafs)", order, leafs.size());
	}
}
