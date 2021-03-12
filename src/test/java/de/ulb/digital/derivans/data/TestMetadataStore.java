package de.ulb.digital.derivans.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.ulb.digital.derivans.DigitalDerivansException;
import de.ulb.digital.derivans.model.DescriptiveData;
import de.ulb.digital.derivans.model.DigitalPage;
import de.ulb.digital.derivans.model.DigitalStructureTree;

/**
 * 
 * Specification of {@link MetadataStore}
 * 
 * @author hartwig
 *
 */
class TestMetadataStore {

	Path path143074601 = Path.of("./src/test/resources/metadata/kitodo2/143074601.xml");
	
	Path path147573602 = Path.of("./src/test/resources/metadata/kitodo2/147573602.xml");

	Path path737429 = Path.of("./src/test/resources/metadata/mets/737429.xml");

	Path path201517 = Path.of("./src/test/resources/metadata/mets/201517.xml");

	Path path226134857 = Path.of("./src/test/resources/metadata/mets/226134857.prep.xml");
	
	Path path993571 = Path.of("./src/test/resources/metadata/mets/993571.ulb.xml");
	
	Path path133573613 = Path.of("./src/test/resources/metadata/mets/133573613.prep.xml");
	
	Path path1237560 = Path.of("./src/test/resources/metadata/mets/1237560.xml");

	@Test
	void testMetadataStoreGetUrn() throws DigitalDerivansException {
		IMetadataStore mds = new MetadataStore(path143074601);
		assertEquals("urn:nbn:de:gbv:3:1-1192015415-143074601-16", mds.getDescriptiveData().getUrn());
	}

	@Test
	void testMetadataStoreGetIdentifier() throws DigitalDerivansException {
		IMetadataStore mds = new MetadataStore(path143074601);
		assertEquals("143074601", mds.getDescriptiveData().getIdentifier());
	}
	
	@Test
	void testMetadataStoreGetIdentifierDifferentSource() throws DigitalDerivansException {
		IMetadataStore mds = new MetadataStore(path1237560);
		assertEquals("323633072", mds.getDescriptiveData().getIdentifier());
	}

	@Test
	void testDigitalPagesWithoutGranularUrn() throws DigitalDerivansException {
		IMetadataStore mds = new MetadataStore(path143074601);
		List<DigitalPage> pages = mds.getDigitalPagesInOrder();
		for (DigitalPage page : pages) {
			assertTrue(page.getIdentifier().isEmpty());
		}
	}
	

	/**
	 * 
	 * Check expected information is extracted from METS/MODS-export of kitodo.production2
	 * 
	 * imported in open data as:
	 * https://opendata.uni-halle.de/handle/1981185920/36228
	 * 
	 * @throws DigitalDerivansException
	 */
	@Test
	void testDescriptiveDataFromKitodo2() throws DigitalDerivansException {
		// arrange
		IMetadataStore mds = new MetadataStore(path147573602);
		
		// act
		DescriptiveData dd = mds.getDescriptiveData();
		
		// assert
		// PDF creator from configuration, not from METS/MODS
		assertTrue(dd.getCreator().isEmpty());
		// mods:recodInfo/mods:recordIdentifier[@source]/text()
		assertEquals("147573602", dd.getIdentifier());
		// mods:titleInfo/mods:title
		assertTrue(dd.getTitle().startsWith("Tractätgen von denen Jüdischen Fabeln und Aberglauben"));
		// mods:accessCondition[type="use and reproduction"]/text()
		assertEquals("CC-BY-SA 3.0 DE", dd.getLicense().get());
		// mods:identifier[@type="urn"]
		assertEquals("urn:nbn:de:gbv:3:1-1192015415-147573602-14", dd.getUrn());
		// mods:role/mods:displayForm/text() IF mods:name/mods:role/mods:roleTerm[@type="code"]/text() = "aut"
		assertEquals("Christian, Magnus", dd.getPerson());
		// mods:originInfo/mods:dateIssued[@keyDate="yes"]/text()
		assertEquals("1718", dd.getYearPublished());
	}

	/**
	 * 
	 * Check expected information is extracted from OAI-record in old VLS 12 format
	 * 
	 * http://digital.bibliothek.uni-halle.de/hd/oai/?verb=GetRecord&metadataPrefix=mets&mode=xml&identifier=737429
	 * 
	 * @throws DigitalDerivansException
	 */
	@Test
	//@Disabled
	void testDescriptiveDataFromVL12OAI() throws DigitalDerivansException {
		// arrange
		IMetadataStore mds = new MetadataStore(path737429);
		
		// act
		DescriptiveData dd = mds.getDescriptiveData();
		
		// assert
		// PDF creator from configuration, not from METS/MODS
		assertTrue(dd.getCreator().isEmpty());
		// mods:recodInfo/mods:recordIdentifier[@source]/text()
		assertEquals("191092622", dd.getIdentifier());
		// mods:titleInfo/mods:title
		assertTrue(dd.getTitle().startsWith("Ode In Solemni Panegyri Avgvstissimo Ac Potentissimo"));
		// mods:identifier[@type="urn"]
		assertEquals("urn:nbn:de:gbv:3:3-21437", dd.getUrn());
		// METS/MODS contains no license information
		assertTrue(dd.getLicense().isEmpty());
		// mods:originInfo/mods:dateIssued[@keyDate="yes"]/text()
		assertEquals("1731", dd.getYearPublished());
		// mods:role/mods:displayForm/text()
		// OR
		// mods:namePart[@type="family"]/text()
		// WITH 
		// IF NOT mods:name/mods:role/mods:roleTerm[@type="code"]/text() = "aut" 
		// IF mods:name/mods:role/mods:roleTerm[@type="code"]/text() = "pbl		
		assertEquals("Brühl", dd.getPerson());
	}
		
	@Test
	void testDigitalPagesOrderOf737429() throws DigitalDerivansException {
		
		// arrange
		IMetadataStore mds = new MetadataStore(path737429);

		// act
		List<DigitalPage> pages = mds.getDigitalPagesInOrder();
		
		// assert
		for (DigitalPage page : pages) {
			assertTrue(page.getIdentifier().isPresent());
		}

		String urn1 = "urn:nbn:de:gbv:3:3-21437-p0001-0";
		String urn2 = "urn:nbn:de:gbv:3:3-21437-p0004-6";
		assertEquals(urn1, pages.get(0).getIdentifier().get());
		assertEquals(urn2, pages.get(3).getIdentifier().get());
		assertEquals("737434.jpg", pages.get(0).getFilePointer());
		assertEquals("737436.jpg", pages.get(1).getFilePointer());
		assertEquals("737437.jpg", pages.get(2).getFilePointer());
		assertEquals("737438.jpg", pages.get(3).getFilePointer());
	}

	@Test
	void testStructureOf737429() throws DigitalDerivansException {
		IMetadataStore mds = new MetadataStore(path737429);

		assertEquals("urn:nbn:de:gbv:3:3-21437", mds.getDescriptiveData().getUrn());
		assertEquals("191092622", mds.getDescriptiveData().getIdentifier());

		DigitalStructureTree dst = mds.getStructure();
		assertNotNull(dst);

		assertTrue(dst.getLabel().startsWith("Ode In Solemni Panegyri"));
		assertEquals(1, dst.getPage());
		assertTrue(dst.hasSubstructures());

		// level 1
		List<DigitalStructureTree> children = dst.getSubstructures();
		assertEquals(2, children.size());
		assertEquals("Titelblatt", children.get(0).getLabel());
		assertEquals(1, children.get(0).getPage());
		assertEquals("[Ode]", children.get(1).getLabel());
		assertEquals(2, children.get(1).getPage());
	}

	@Test
	void testStructureOf201517() throws DigitalDerivansException {
		IMetadataStore mds = new MetadataStore(path201517);

		assertEquals("urn:nbn:de:gbv:3:3-6252", mds.getDescriptiveData().getUrn());
		assertEquals("535610149", mds.getDescriptiveData().getIdentifier());

		DigitalStructureTree dst = mds.getStructure();
		assertNotNull(dst);

		assertNotNull(dst.getLabel());
		assertEquals(1, dst.getPage());
		assertTrue(dst.hasSubstructures());

		// level 1
		List<DigitalStructureTree> children = dst.getSubstructures();
		assertEquals(12, children.size());
		assertEquals("Vorderdeckel", children.get(0).getLabel());
		assertEquals(1, children.get(0).getPage());
		assertEquals("Kupfertitel", children.get(1).getLabel());
		assertEquals(6, children.get(1).getPage());

		// level 1+2
		assertEquals("Liber Primus,", children.get(5).getLabel());
		assertTrue(children.get(5).hasSubstructures());
		assertEquals(2, children.get(5).getSubstructures().size());

		// level 1+2+3
		assertEquals(
				"Continuatio Historiae Ecclesiasticae Iohannis Micraelii, Secunda Hac Editione Emendata & plurimis locis aucta à Daniele Hartnaccio, Pomerano.",
				children.get(9).getLabel());
		assertEquals(4, children.get(9).getSubstructures().size());
		assertEquals(11, children.get(9).getSubstructures().get(1).getSubstructures().size());
	}


	@Test
	void testMetadataIsUpdated737429(@TempDir Path tempDir) throws Exception {
		// arrange
		Path sourcePathFile = Path.of("src/test/resources/metadata/mets/737429.xml");
		Path targetPathFile = tempDir.resolve("737429.xml");
		if (Files.exists(targetPathFile)) {
			Files.delete(targetPathFile);
		}
		Files.copy(sourcePathFile, targetPathFile);
		IMetadataStore mds = new MetadataStore(targetPathFile);

		// act
		String identifier = mds.getDescriptiveData().getIdentifier();
		boolean renderPDFOutcome = mds.enrichPDF(identifier);

		// assert
		assertTrue(renderPDFOutcome);
	}

	/**
	 * 
	 * Example of invalid Test Data - contains links to non-existing physical
	 * structures and therefore cannot generate proper Mappings for this part of
	 * Document Structure
	 * 
	 * @throws Exception
	 */
	@Test
	void testStructureOf226134857() throws Exception {

		// arrange
		IMetadataStore mds = new MetadataStore(path226134857);

		// act
		DigitalStructureTree tree = mds.getStructure();

		// assert
		for (DigitalStructureTree subTree : tree.getSubstructures()) {
			if (subTree.hasSubstructures()) {
				for (DigitalStructureTree subSubTree : subTree.getSubstructures()) {
					assertFalse(subSubTree.hasSubstructures());
					assertTrue(subSubTree.getPage() > 0);
				}
			}
			assertTrue(subTree.getPage() > 0);
		}
	}

	@Test
	void testStructurOf133573613() throws Exception {

		// arrange
		
		IMetadataStore store = new MetadataStore(path133573613);

		// act
		DigitalStructureTree tree = store.getStructure();

		// assert
		assertEquals(5, tree.getSubstructures().size());
	}
	
}
