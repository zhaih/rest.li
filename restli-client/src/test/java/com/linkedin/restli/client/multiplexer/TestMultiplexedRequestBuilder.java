/*
   Copyright (c) 2015 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.restli.client.multiplexer;


import com.google.common.collect.ImmutableList;
import com.linkedin.common.callback.Callback;
import com.linkedin.data.template.StringMap;
import com.linkedin.restli.client.CreateRequest;
import com.linkedin.restli.client.GetRequestBuilder;
import com.linkedin.restli.client.Request;
import com.linkedin.restli.client.Response;
import com.linkedin.restli.client.RestLiCallbackAdapter;
import com.linkedin.restli.client.RestLiEncodingException;
import com.linkedin.restli.client.RestliRequestOptions;
import com.linkedin.restli.client.test.TestRecord;
import com.linkedin.restli.common.EmptyRecord;
import com.linkedin.restli.common.HttpMethod;
import com.linkedin.restli.common.multiplexer.IndividualBody;
import com.linkedin.restli.common.multiplexer.IndividualRequest;
import com.linkedin.restli.common.multiplexer.IndividualRequestArray;
import com.linkedin.restli.common.multiplexer.MultiplexedRequestContent;
import com.linkedin.restli.internal.common.CookieUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.Collection;
import java.util.Collections;


public class TestMultiplexedRequestBuilder extends MultiplexerTestBase
{
  private final NoOpCallback<TestRecord> callback1 = new NoOpCallback<TestRecord>();
  private final NoOpCallback<TestRecord> callback2 = new NoOpCallback<TestRecord>();

  @Test(expectedExceptions = {IllegalStateException.class})
  public void testEmpty() throws RestLiEncodingException
  {
    MultiplexedRequestBuilder
        .createParallelRequest()
        .build();
  }

  @Test
  public void testParallel() throws RestLiEncodingException
  {
    MultiplexedRequest multiplexedRequest = MultiplexedRequestBuilder
        .createParallelRequest()
        .addRequest(request1, callback1)
        .addRequest(request2, callback2)
        .build();

    //verify requests
    IndividualRequest ir1 = fakeIndividualRequest(0, getUri(ID1), Collections.<IndividualRequest>emptyList());
    IndividualRequest ir2 = fakeIndividualRequest(1, getUri(ID2), Collections.<IndividualRequest>emptyList());
    MultiplexedRequestContent expectedRequests = new MultiplexedRequestContent();
    expectedRequests.setRequests(new IndividualRequestArray(ImmutableList.of(ir1, ir2)));

    Assert.assertEquals(multiplexedRequest.getContent(), expectedRequests);
    verifyCallbacks(multiplexedRequest);
  }

  @Test
  public void testSequential() throws RestLiEncodingException
  {
    MultiplexedRequest multiplexedRequest = MultiplexedRequestBuilder
        .createSequentialRequest()
        .addRequest(request1, callback1)
        .addRequest(request2, callback2)
        .build();

    //verify requests
    IndividualRequest ir2 = fakeIndividualRequest(1, getUri(ID2), Collections.<IndividualRequest>emptyList());
    IndividualRequest ir1 = fakeIndividualRequest(0, getUri(ID1), Collections.singletonList(ir2));
    MultiplexedRequestContent expectedRequests = new MultiplexedRequestContent();
    expectedRequests.setRequests(new IndividualRequestArray(ImmutableList.of(ir1)));

    Assert.assertEquals(multiplexedRequest.getContent(), expectedRequests);
    verifyCallbacks(multiplexedRequest);
  }

  @Test
  public void testCookies() throws RestLiEncodingException
  {
    final HttpCookie cookie1 = new HttpCookie("testCookie1", "testCookieValue1");
    final HttpCookie cookie2 = new HttpCookie("testCookie2", "testCookieValue2");
    // create a request with two cookies
    Request<TestRecord> requestWithCookie = new GetRequestBuilder<Integer, TestRecord>(BASE_URI, TestRecord.class, RESOURCE_SPEC, RestliRequestOptions.DEFAULT_OPTIONS)
      .id(ID1)
      .setHeaders(HEADERS)
      .addCookie(cookie1)
      .addCookie(cookie2)
      .build();

    MultiplexedRequest multiplexedRequest = MultiplexedRequestBuilder
      .createSequentialRequest()
      .addRequest(requestWithCookie, callback2)
      .build();
    IndividualRequest ir = multiplexedRequest.getContent().getRequests().get(0);
    Collection<HttpCookie> cookies = CookieUtil.decodeCookies(ir.getCookies());
    final String errorMessageTemplate = "Unable to find cookie '%s' from cookie list in IndividualRequest: " + cookies.toString();
    Assert.assertTrue(cookies.contains(cookie1), String.format(errorMessageTemplate, cookie1));
    Assert.assertTrue(cookies.contains(cookie2), String.format(errorMessageTemplate, cookie2));
  }

  @Test
  public void testBody() throws IOException
  {
    TestRecord entity = fakeEntity(0);
    CreateRequest<TestRecord> request = fakeCreateRequest(entity);
    NoOpCallback<EmptyRecord> callback = new NoOpCallback<EmptyRecord>();

    MultiplexedRequest multiplexedRequest = MultiplexedRequestBuilder
        .createSequentialRequest()
        .addRequest(request, callback)
        .build();

    IndividualRequest individualRequest = new IndividualRequest()
        .setId(0)
        .setMethod(HttpMethod.POST.name())
        .setHeaders(new StringMap(HEADERS))
        .setRelativeUrl(BASE_URI)
        .setBody(new IndividualBody(entity.data()))
        .setDependentRequests(new IndividualRequestArray());
    MultiplexedRequestContent expectedRequests = new MultiplexedRequestContent();
    expectedRequests.setRequests(new IndividualRequestArray(ImmutableList.of(individualRequest)));

    Assert.assertEquals(multiplexedRequest.getContent(), expectedRequests);
  }

  private void verifyCallbacks(MultiplexedRequest request)
  {
    Assert.assertEquals(request.getCallbacks().size(), 2);
    Assert.assertSame(request.getCallbacks().get(0).getClass(), RestLiCallbackAdapter.class);
    Assert.assertSame(request.getCallbacks().get(1).getClass(), RestLiCallbackAdapter.class);
  }

  /**
   * Callbacks.empty() can not be used here, because it always return the same instance.
   */
  private static class NoOpCallback<T> implements Callback<Response<T>>
  {
    @Override
    public void onError(Throwable e)
    {
    }

    @Override
    public void onSuccess(Response<T> result)
    {
    }
  }
}
