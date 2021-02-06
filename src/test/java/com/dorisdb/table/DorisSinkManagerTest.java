package com.dorisdb.table;

import org.apache.flink.runtime.util.ExecutorThreadFactory;
import org.apache.flink.shaded.guava18.com.google.common.collect.Lists;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.dorisdb.DorisSinkBaseTest;
import com.dorisdb.manager.DorisQueryVisitor;
import com.dorisdb.manager.DorisSinkManager;
import mockit.Expectations;
import mockit.Mocked;

public class DorisSinkManagerTest extends DorisSinkBaseTest {

	@Test
	public void testValidateTableStructure(@Mocked DorisQueryVisitor v) {
		new Expectations(){
			{
				v.getTableColumnsMetaData();
				result = DORIS_TABLE_META.keySet().stream().map(k -> new HashMap<String, Object>(){{
					put("COLUMN_NAME", k);
					put("DATA_TYPE", DORIS_TABLE_META.get(k).toString());
				}}).collect(Collectors.toList());;
			}
		};
		OPTIONS.getSinkStreamLoadProperties().remove("columns");
		assertTrue(!OPTIONS.hasColumnMappingProperty());
		// test succeeded
		try {
			new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
		} catch (Exception e) {
			throw e;
		}
		// test failed
		new Expectations(){
			{
				v.getTableColumnsMetaData();
				result = Lists.newArrayList();
			}
		};
		String exMsg = "";
		try {
			new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
		} catch (Exception e) {
			exMsg = e.getMessage();
		}
		assertTrue(exMsg.length() > 0);
		// test failed
		new Expectations(){
			{
				v.getTableColumnsMetaData();
				result = DORIS_TABLE_META.keySet().stream().map(k -> new HashMap<String, Object>(){{
					put("COLUMN_NAME", k);
					put("DATA_TYPE", "varchar");
				}}).collect(Collectors.toList());;
			}
		};
		exMsg = "";
		try {
			new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
		} catch (Exception e) {
			exMsg = e.getMessage();
		}
		assertTrue(exMsg.length() > 0);
	}

	@Test
	public void testWriteMaxBatch(@Mocked DorisQueryVisitor v) throws IOException {
		long maxRows = OPTIONS.getSinkMaxRows();
		stopHttpServer();
		try {
			DorisSinkManager mgr = new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
			for (int i = 0; i < maxRows - 1; i++) {
				mgr.writeRecord("test record");
			}
		} catch (Exception e) {
			throw e;
		}
		String exMsg = "";
		try {
			DorisSinkManager mgr = new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
			for (int i = 0; i < maxRows; i++) {
				mgr.writeRecord("test record");
			}
		} catch (Exception e) {
			exMsg = e.getMessage();
		}
		assertTrue(0 < exMsg.length());
	}

	@Test
	public void testWriteMaxBytes(@Mocked DorisQueryVisitor v) throws IOException {
		long maxSize = OPTIONS.getSinkMaxBytes();
		stopHttpServer();
		int rowLength = 100000;
		try {
			DorisSinkManager mgr = new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
			for (int i = 0; i < maxSize / rowLength - 1; i++) {
				mgr.writeRecord(new String(new char[rowLength]));
			}
		} catch (Exception e) {
			throw e;
		}
		String exMsg = "";
		try {
			DorisSinkManager mgr = new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
			for (int i = 0; i < maxSize / rowLength + 1; i++) {
				mgr.writeRecord(new String(new char[rowLength]));
			}
			mgr.writeRecord(new String(new char[rowLength]));
		} catch (Exception e) {
			exMsg = e.getMessage();
		}
		assertTrue(0 < exMsg.length());
	}

	@Test
	public void testWriteMaxRetries(@Mocked DorisQueryVisitor v) throws IOException {
		int maxRetries = OPTIONS.getSinkMaxRetries();
		if (maxRetries <= 0) return;
		stopHttpServer();
		mockSuccessResponse();
		String exMsg = "";
		try {
			DorisSinkManager mgr = new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
			for (int i = 0; i < OPTIONS.getSinkMaxRows(); i++) {
				mgr.writeRecord("");
			}
		} catch (Exception e) {
			exMsg = e.getMessage();
		}
		assertTrue(0 < exMsg.length());

		Executors.newScheduledThreadPool(1, new ExecutorThreadFactory("test")).schedule(() -> {
			try {
				createHttpServer();
			} catch (Exception e) {}
		}, maxRetries * 1000 - 100, TimeUnit.MILLISECONDS);
		try {
			DorisSinkManager mgr = new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
			for (int i = 0; i < OPTIONS.getSinkMaxRows(); i++) {
				mgr.writeRecord("");
			}
		} catch (Exception e) {
			throw e;
		}
	}

	@Test
	public void testFlush(@Mocked DorisQueryVisitor v) throws IOException {
		mockSuccessResponse();
		String exMsg = "";
		try {
			DorisSinkManager mgr = new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
			mgr.writeRecord("");
			mgr.flush();
		} catch (Exception e) {
			exMsg = e.getMessage();
			throw e;
		}
		assertEquals(0, exMsg.length());
		try {
			DorisSinkManager mgr = new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
			mgr.writeRecord("");
			mgr.flush();
		} catch (Exception e) {
			exMsg = e.getMessage();
			throw e;
		}
		assertEquals(0, exMsg.length());
		stopHttpServer();
		try {
			DorisSinkManager mgr = new DorisSinkManager(OPTIONS, TABLE_SCHEMA);
			mgr.writeRecord("");
			mgr.flush();
		} catch (Exception e) {
			exMsg = e.getMessage();
		}
		assertTrue(0 < exMsg.length());
	}
}