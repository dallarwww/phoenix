/*******************************************************************************
 * Copyright (c) 2013, Salesforce.com, Inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *     Neither the name of Salesforce.com nor the names of its contributors may 
 *     be used to endorse or promote products derived from this software without 
 *     specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package phoenix.query;

import static org.junit.Assert.*;
import static phoenix.util.TestUtil.PHOENIX_JDBC_URL;
import static phoenix.util.TestUtil.TEST_PROPERTIES;

import java.sql.*;
import java.util.Properties;

import org.junit.Test;

import phoenix.util.PhoenixRuntime;


/**
 * 
 * Extended tests for Phoenix JDBC implementation
 * 
 */
public class ExtendedQueryExecTest extends BaseClientMangedTimeTest {

    @Test
    public void testToDateFunctionBind() throws Exception {
        long ts = nextTimestamp();
        Date date = new Date(1);
        String tenantId = getOrganizationId();

        initATableValues(tenantId, getDefaultSplits(tenantId),date, ts);
        
        Properties props = new Properties(TEST_PROPERTIES);
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts+1));
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        try {
            String query = "SELECT a_date FROM atable WHERE organization_id='" + tenantId + "' and a_date < TO_DATE(?)";
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, "1970-1-1 12:00:00");
            ResultSet rs = statement.executeQuery();
            verifyDateResultSet(rs, date, 3);
        } finally {
            conn.close();
        }
    }

    @Test
    public void testTypeMismatchToDateFunctionBind() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        initATableValues(tenantId, getDefaultSplits(tenantId),null, ts);
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        try {
            String query = "SELECT a_date FROM atable WHERE organization_id='" + tenantId + "' and a_date < TO_DATE(?)";
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setDate(1, new Date(2));
            statement.executeQuery();
            fail();
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("Type mismatch for TO_DATE argument"));
        } finally {
            conn.close();
        }
    }

    /**
     * Basic tests for date function
     * Related bug: W-1190856
     * @throws Exception
     */
    @Test
    public void testDateFunctions() throws Exception {
        long ts = nextTimestamp();
        Date date = new Date(1);
        String tenantId = getOrganizationId();

        initATableValues(tenantId, getDefaultSplits(tenantId),date, ts);
        
        Properties props = new Properties(TEST_PROPERTIES);
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts+1));
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        try {
            ResultSet rs;
            String queryPrefix = "SELECT a_date FROM atable WHERE organization_id='" + tenantId + "' and ";

            String queryDateArg = "a_date < TO_DATE('1970-1-1 12:00:00')";
            rs = getResultSet(conn, queryPrefix + queryDateArg);
            verifyDateResultSet(rs, date, 3);

            // TODO: Bug #1 - Result should be the same as the the case above
//          queryDateArg = "a_date < TO_DATE('70-1-1 12:0:0')";
//          rs = getResultSet(conn, queryPrefix + queryDateArg);
//          verifyDateResultSet(rs, date, 3);

            // TODO: Bug #2 - Exception should be generated for invalid date/time
//          queryDateArg = "a_date < TO_DATE('999-13-32 24:60:60')";
//          try {
//              getResultSet(conn, queryPrefix + queryDateArg);
//              fail("Expected SQLException");
//          } catch (SQLException ex) {
//              // expected
//          }
            
            queryDateArg = "a_date >= TO_DATE('1970-1-2 23:59:59') and a_date <= TO_DATE('1970-1-3 0:0:1')";
            rs = getResultSet(conn, queryPrefix + queryDateArg);
            verifyDateResultSet(rs, new Date(date.getTime() + (2*60*60*24*1000)), 3);

        } finally {
            conn.close();
        }
    }
    
    /**
     * aggregation - group by
     * @throws Exception
     */
    @Test
    public void testDateGroupBy() throws Exception {
        long ts = nextTimestamp();
        Date date = new Date(1);
        String tenantId = getOrganizationId();

        initATableValues(tenantId, getDefaultSplits(tenantId),date, ts);
        
        Properties props = new Properties(TEST_PROPERTIES);
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts+1));
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        try {
            ResultSet rs;
            String query = "SELECT a_date, count(1) FROM atable WHERE organization_id='" + tenantId + "' group by a_date";
            rs = getResultSet(conn, query);
            
            /* 3 rows in expected result:
             * 1969-12-31   3
             * 1970-01-01   3
             * 1970-01-02   3
             * */
                        
            assertTrue(rs.next());
            assertEquals(date, rs.getDate(1));
            assertEquals(3, rs.getInt(2));
            
            // the following assertions fails
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(2));
            assertFalse(rs.next());
            

        } finally {
            conn.close();
        }
    }
    
    private ResultSet getResultSet(Connection conn, String query) throws SQLException {
        PreparedStatement statement = conn.prepareStatement(query);
        ResultSet rs = statement.executeQuery();
        return rs;
    }
    
    private void verifyDateResultSet(ResultSet rs, Date date, int rowCount) throws SQLException {
        for (int i=0; i<rowCount; i++) {
            assertTrue(rs.next());
            assertEquals(date, rs.getDate(1));
        }
        assertFalse(rs.next());
    }
}
