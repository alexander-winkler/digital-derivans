package de.ulb.digital.derivans.derivate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.ulb.digital.derivans.DigitalDerivansException;
import de.ulb.digital.derivans.model.DerivansData;

/**
 * 
 * Basic Abstract Image Derivate Creation
 * 
 * @author hartwig
 *
 */
public abstract class ImageDerivateer extends BaseDerivateer {

	public static final Integer DEFAULT_QUALITY = 80;

	public static final int DEFAULT_POOLSIZE = 2;

	public static final int MIN_FREE_CORES = 1;

	protected Boolean insertIntoMets;

	protected Integer quality;

	protected final Path inputDir;

	protected final Path outputDir;

	protected static final Logger LOGGER = LogManager.getLogger(ImageDerivateer.class);

	protected int poolSize;

	protected Integer maximal;

	protected ImageProcessor imageProcessor;
	
	protected ImageDerivateer(DerivansData input, DerivansData output) {
		super(input, output);
		this.insertIntoMets = false;
		this.quality = DEFAULT_QUALITY;
		this.poolSize = DEFAULT_POOLSIZE;
		this.inputDir = input.getPath();
		this.outputDir = output.getPath();
		this.imageProcessor = new ImageProcessor();
	}

	public Integer getQuality() {
		return this.quality;
	}
	
	public void setImageProcessor(ImageProcessor processor) {
		this.imageProcessor = processor;
	}

	public void setPoolsize(Integer poolSize) {
		int cores = Runtime.getRuntime().availableProcessors();
		int limit = cores - MIN_FREE_CORES;
		if (poolSize != null && poolSize > 0) {
			if (poolSize >= limit) {
				this.poolSize = limit;
			} else {
				this.poolSize = poolSize;
			}
		} else {
			this.poolSize = MIN_FREE_CORES;
			LOGGER.warn("invalid poolsize provided:'{}', fallback to '{}'", poolSize, this.poolSize);
		}
	}
	
	protected int getPoolSize() {
		return this.poolSize;
	}

	public void setMaximal(Integer maximal) {
		this.maximal = maximal;
	}

	protected boolean runWithPool(Runnable runnable) throws DigitalDerivansException {
		try {
			ForkJoinPool threadPool = new ForkJoinPool(poolSize);
			threadPool.submit(runnable).get();
			threadPool.shutdown();
			return true;
		} catch (ExecutionException e) {
			LOGGER.error(e);
			throw new DigitalDerivansException(e);
		} catch (InterruptedException e) {
			LOGGER.error(e);
			Thread.currentThread().interrupt();
		}
		return false;
	}

	@Override
	public boolean create() throws DigitalDerivansException {
		
		// basic precondition: output directory shall exist
		if (!Files.exists(this.getOutput().getPath())) {
			try {
				Files.createDirectory(this.getOutput().getPath());
			} catch (IOException e) {
				LOGGER.error(e);
				throw new DigitalDerivansException(e);
			}
		}

		String msg = String.format("process '%02d' images in %s with quality %02d in %02d threads",
				this.digitalPages.size(), inputDir, this.getQuality(), this.poolSize);
		LOGGER.info(msg);

		Instant start = Instant.now();

		// forward to actual image creation implementation
		// subject to each concrete subclass
		boolean isSuccess = forward();

		Instant finish = Instant.now();
		long secsElapsed = Duration.between(start, finish).toSecondsPart();
		long minsElapsed = Duration.between(start, finish).toMinutesPart();

		if (isSuccess) {
			String msg2 = String.format("created '%02d' images at '%s' in '%dm%02ds'",
			 this.digitalPages.size(), outputDir, minsElapsed, secsElapsed);
			LOGGER.info(msg2);
			return true;
		}
		return false;
	}

	/**
	 * 
	 * Forward actual Image generation to Subclasses
	 * 
	 * @return
	 * @throws DigitalDerivansException
	 */
	public abstract boolean forward() throws DigitalDerivansException;

	/**
	 * 
	 * Gather Image Path Information regarding a specific image type specified by fileFilter
	 * 
	 * @param path
	 * @param fileFilter
	 * @return
	 * @throws IOException
	 */
//	public static List<Path> getFilePaths(Path path, Predicate<Path> fileFilter) throws IOException {
//		try (Stream<Path> filesList = Files.list(path)) {
//			return filesList.filter(Files::isRegularFile).filter(fileFilter).sorted().collect(Collectors.toList());
//		}
//	}
}
