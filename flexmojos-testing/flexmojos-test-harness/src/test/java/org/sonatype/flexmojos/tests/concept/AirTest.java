package org.sonatype.flexmojos.tests.concept;

import org.testng.annotations.Test;

public class AirTest
    extends AbstractConceptTest
{

    @Test
    public void airApp()
        throws Exception
    {
        standardConceptTester( "simple-air" );
    }
}
