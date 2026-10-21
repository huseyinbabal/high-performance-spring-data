"""Prints the request a `make` target sends to Elasticsearch, in Kibana Dev Tools format.

The bodies mirror EventQueryService (and the explain/profile targets) one to one, so pasting
them into Kibana gives the same hits the app returns.
"""
import json
import os
import sys

env = lambda k, d=None: os.environ.get(k) or d
q = env('Q', 'akustik')


def text_query():
    return {
        # must = recall, should = ranking (EventQueryService.handle)
        'must': {'match': {'all': {'query': q, 'operator': 'and', 'fuzziness': 'AUTO'}}},
        'should': {'multi_match': {'query': q, 'fields': ['name^3', 'artist^3']}},
    }


def facets():
    return {'city': {'terms': {'field': 'city', 'size': 10}},
            'category': {'terms': {'field': 'category', 'size': 10}}}


def search(filters=()):
    page = int(env('PAGE', '0'))
    starts = 'now/m' if env('V') == 'v3' else 'now'
    return 'GET events/_search', {
        'from': page * 20, 'size': 20,
        'query': {'bool': {**text_query(), 'filter': [{'range': {'startsAt': {'gte': starts}}}, *filters]}},
        'aggs': facets(),
    }


def filtered():
    return search([
        {'term': {'city': env('CITY', 'İstanbul')}},
        {'term': {'category': env('CATEGORY', 'rock')}},
        {'range': {'minPrice': {'lte': float(env('MAX', '300'))}}},
    ])


def upcoming(**extra):
    return 'GET events/_search', {
        'track_total_hits': False,
        'query': {'bool': {'filter': [{'range': {'startsAt': {'gte': 'now'}}}]}},
        'sort': [{'startsAt': 'asc'}, {'id': 'asc'}],
        **extra,
    }


def page():
    return upcoming(**{'from': int(env('PAGE', '0')) * 20, 'size': 20})


def page_after():
    after = env('AFTER')
    body = {'size': 20}
    if after:
        body['search_after'] = [int(v) for v in after.split(',')]
    return upcoming(**body)


def suggest():
    return 'GET events/_search', {
        'size': 50,
        'query': {'multi_match': {'query': env('Q', 'gece yol'), 'type': 'bool_prefix',
                                  'fields': ['name.suggest', 'name.suggest._2gram', 'name.suggest._3gram']}},
    }


def calendar():
    filters = [{'range': {'startsAt': {'gte': 'now'}}}]
    if env('CITY', 'İzmir'):
        filters.append({'term': {'city': env('CITY', 'İzmir')}})
    return 'GET events/_search', {
        'size': 0,
        'query': {'bool': {'filter': filters}},
        'aggs': {'months': {'date_histogram': {'field': 'startsAt', 'calendar_interval': 'month'},
                            'aggs': {'avgPrice': {'avg': {'field': 'minPrice'}}}}},
    }


def explain():
    return 'GET events/_explain/' + env('ID', '29643'), {'query': {'bool': text_query()}}


def profile():
    _, body = search()
    body.update(profile=True)
    return 'GET events/_search?request_cache=false', body


def analyze():
    return 'GET events/_analyze', {'analyzer': 'folded', 'text': env('TEXT', 'Kuzey Rüzgârı İstanbul')}


targets = {'search': search, 'filter': filtered, 'page': page, 'page-after': page_after, 'suggest': suggest,
           'calendar': calendar, 'explain': explain, 'profile': profile, 'analyze': analyze}

name = sys.argv[1] if len(sys.argv) > 1 else 'search'
if name not in targets:
    sys.exit('T= one of: ' + ', '.join(targets))
line, body = targets[name]()
print(line)
print(json.dumps(body, indent=2, ensure_ascii=False))
