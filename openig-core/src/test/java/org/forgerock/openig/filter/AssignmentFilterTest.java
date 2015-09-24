/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.StaticResponseHandler;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AssignmentFilterTest {

    final static String VALUE = "SET";

    @DataProvider
    private Object[][] validConfigurations() {
        return new Object[][] {
            { json(object(field("onRequest", array(
                    object(
                        field("target", "${exchange.attributes.target}"),
                        field("value", VALUE)))))) },
            { json(object(field("onRequest", array(
                    object(
                        field("target", "${exchange.attributes.target}"),
                        field("value", VALUE),
                        field("condition", "${1==1}")))))) },
            { json(object(field("onResponse", array(
                    object(
                        field("target", "${exchange.attributes.target}"),
                        field("value", VALUE)))))) },
            { json(object(field("onResponse", array(
                    object(
                        field("target", "${exchange.attributes.target}"),
                        field("value", VALUE),
                        field("condition", "${1==1}")))))) } };
    }

    @DataProvider
    private Object[][] invalidConfigurations() {
        return new Object[][] {
            /* Missing target. */
            { json(object(
                    field("onRequest", array(object(
                            field("value", VALUE)))))) },
            /* Missing target (bis). */
            { json(object(
                    field("onResponse", array(object(
                            field("value", VALUE),
                            field("condition", "${1==1}")))))) } };
    }

    @Test(dataProvider = "invalidConfigurations", expectedExceptions = JsonValueException.class)
    public void shouldFailToCreateHeapletWhenRequiredAttributeIsMissing(final JsonValue config) throws Exception {
        buildAssignmentFilter(config);
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws HeapException, Exception {
        final AssignmentFilter filter = buildAssignmentFilter(config);
        final Exchange exchange = new Exchange();
        final StaticResponseHandler handler = new StaticResponseHandler(Status.OK);
        chainOf(handler, filter).handle(exchange, null).get();
        assertThat(exchange.getAttributes().get("target")).isEqualTo(VALUE);
    }

    @Test
    public void shouldSucceedToUnsetVar() throws Exception {
        final AssignmentFilter filter = new AssignmentFilter();
        final Expression<String> target = Expression.valueOf("${exchange.attributes.target}", String.class);
        filter.addRequestBinding(target, null);

        final Exchange exchange = new Exchange();
        exchange.getAttributes().put("target", "UNSET");
        final StaticResponseHandler handler = new StaticResponseHandler(Status.OK);
        final Handler chain = chainOf(handler, singletonList((Filter) filter));
        assertThat(target.eval(exchange)).isEqualTo("UNSET");

        chain.handle(exchange, null).get();
        assertThat(target.eval(exchange)).isNull();
    }

    @Test
    public void onRequest() throws Exception {
        AssignmentFilter filter = new AssignmentFilter();
        final Expression<String> target = Expression.valueOf("${exchange.attributes.newAttr}", String.class);
        filter.addRequestBinding(target,
                                 Expression.valueOf("${exchange.request.method}", String.class));

        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setMethod("DELETE");
        final StaticResponseHandler handler = new StaticResponseHandler(Status.OK);
        Chain chain = new Chain(handler, singletonList((Filter) filter));
        assertThat(target.eval(exchange)).isNull();
        chain.handle(exchange, exchange.getRequest()).get();
        assertThat(exchange.getAttributes().get("newAttr")).isEqualTo("DELETE");
    }

    @Test
    public void shouldChangeUriOnRequest() throws Exception {
        AssignmentFilter filter = new AssignmentFilter();
        filter.addRequestBinding(Expression.valueOf("${exchange.request.uri}", String.class),
                                 Expression.valueOf("www.forgerock.com", String.class));

        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setUri("www.example.com");

        Chain chain = new Chain(new StaticResponseHandler(Status.OK), singletonList((Filter) filter));

        chain.handle(exchange, exchange.getRequest()).get();
        assertThat(exchange.getRequest().getUri().toString()).isEqualTo("www.forgerock.com");
    }

    @Test
    public void onResponse() throws Exception {
        AssignmentFilter filter = new AssignmentFilter();
        final Expression<String> target = Expression.valueOf("${exchange.attributes.newAttr}", String.class);
        filter.addResponseBinding(target,
                                  Expression.valueOf("${exchange.response.status.code}", Integer.class));

        Exchange exchange = new Exchange();
        Chain chain = new Chain(new StaticResponseHandler(Status.OK), singletonList((Filter) filter));
        assertThat(target.eval(exchange)).isNull();
        chain.handle(exchange, exchange.getRequest()).get();
        assertThat(exchange.getAttributes().get("newAttr")).isEqualTo(200);
    }

    private AssignmentFilter buildAssignmentFilter(final JsonValue config) throws Exception {
        final AssignmentFilter.Heaplet heaplet = new AssignmentFilter.Heaplet();
        return (AssignmentFilter) heaplet.create(Name.of("myAssignmentFilter"),
                                                 config,
                                                 buildDefaultHeap());
    }
}
