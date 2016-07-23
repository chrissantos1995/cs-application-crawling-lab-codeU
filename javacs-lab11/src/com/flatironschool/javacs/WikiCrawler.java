package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Node;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {

		if(queue.isEmpty())
			return null;

		// URL to crawl
		String url = queue.poll();

		System.out.println("Checking URL: " + url);

		// Elements to iterate through
		Elements paragraphs;

		if(testing)
			paragraphs = wf.readWikipedia(url);
		else
			paragraphs = wf.fetchWikipedia(url);

		// Index the page if testing or if not already indexed
		if(testing || !index.isIndexed(url))
			index.indexPage(url, paragraphs);
		else
			return null;

		// Queue the internal links of the page
		queueInternalLinks(paragraphs);

		return url;
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
       
		// Search for internal link
		for(Element p : paragraphs) {

			for(Element el : p.getAllElements()) {

				if(isInternalLink(el)) {

					//System.out.println("Found link!");

					// Add internal link to the queue
					String urlToQueue = el.attr("href");
					if(!urlToQueue.contains("https://en.wikipedia.org")) {
						urlToQueue = "https://en.wikipedia.org" + urlToQueue;
					}

					queue.offer(urlToQueue);
				}
			}
		}
	}

	boolean isInternalLink(Element el) {

		//System.out.println("Checking if el is link");

		//System.out.println("\tel has tagname: " + el.tagName());
		//System.out.println("\tel has link: " + el.attr("href"));
		//System.out.println("\tel link contains wiki/: " + el.attr("href").contains("wiki/"));

		boolean isLink = el.tagName().equals("a");
		boolean isInternalLink = isLink && el.attr("href").contains("wiki/");

		// Check if element is anchor tag pointing to a wikipedia page
		return isInternalLink;
	}	

	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
