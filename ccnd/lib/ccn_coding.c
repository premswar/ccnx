#include <fcntl.h>
#include <limits.h>
#include <stddef.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <ccn/charbuf.h>
#include <ccn/coding.h>

struct ccn_decoder_stack_item {
    size_t nameindex; /* byte index into stringstack */
    size_t itemcount;
    size_t savedss;
    enum ccn_vocab saved_schema;
    int saved_schema_state;
    struct ccn_decoder_stack_item *link;
};

struct ccn_decoder {
    int state;
    int tagstate;
    int bits;
    size_t numval;
    uintmax_t bignumval;
    enum ccn_vocab schema;
    int sstate;
    struct ccn_decoder_stack_item *stack;
    struct ccn_charbuf *stringstack;
};

struct ccn_decoder *
ccn_decoder_create(void)
{
    struct ccn_decoder *d;
    d = calloc(1, sizeof(*d));
    d->stringstack = ccn_charbuf_create();
    if (d->stringstack == NULL) {
        free(d);
        d = NULL;
    }
    d->schema = CCN_NO_SCHEMA;
    return(d);
}

struct ccn_decoder_stack_item *
ccn_decoder_push(struct ccn_decoder *d) {
    struct ccn_decoder_stack_item *s;
    s = calloc(1, sizeof(*s));
    if (s != NULL) {
        s->link = d->stack;
        s->savedss = d->stringstack->length;
        s->saved_schema = d->schema;
        s->saved_schema_state = d->sstate;
        d->stack = s;
    }
    return(s);
}

void
ccn_decoder_pop(struct ccn_decoder *d) {
    struct ccn_decoder_stack_item *s = d->stack;
    if (s != NULL) {
        d->stack = s->link;
        d->stringstack->length = s->savedss;
        d->schema = s->saved_schema;
        d->sstate = s->saved_schema_state;
        free(s);
    }
}

void
ccn_decoder_destroy(struct ccn_decoder **dp)
{
    struct ccn_decoder *d = *dp;
    if (d != NULL) {
        while (d->stack != NULL) {
            ccn_decoder_pop(d);
        }
        ccn_charbuf_destroy(&(d->stringstack));
        free(d);
        *dp = NULL;
    }
}

static const char Base64[] =
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

ssize_t
ccn_decoder_decode(struct ccn_decoder *d, unsigned char p[], size_t n)
{
    int state = d->state;
    int tagstate = 0;
    size_t numval = d->numval;
    ssize_t i = 0;
    unsigned char c;
    size_t chunk;
    struct ccn_decoder_stack_item *s;
    while (i < n) {
        switch (state) {
            case 0: /* start new thing */
                if (tagstate > 1 && tagstate-- == 2) {
                    printf("\""); /* close off the attribute value */
                    ccn_decoder_pop(d);
                } 
                if (p[i] == CCN_CLOSE) {
                    i++;
                    s = d->stack;
                    if (s == NULL || tagstate > 1) {
                        state = -__LINE__;
                        break;
                    }
                    if (tagstate == 1) {
                        tagstate = 0;
                        printf("/>");
                    }
                    else if (d->schema == CCN_PROCESSING_INSTRUCTIONS) {
                        printf("?>");
                        if (d->sstate != 2) {
                            state = -__LINE__;
                            break;
                        }
                    }
                    else {
                        printf("</%s>", d->stringstack->buf + s->nameindex);
                    }
                    ccn_decoder_pop(d);
                    break;
                }
                numval = 0;
                state = 1;
                /* FALLTHRU */
            case 1: /* parsing numval */
                c = p[i++];
                if (c != (c & 127)) {
                    if (numval > (numval << 7)) {
                        state = 9;
                        d->bignumval = numval;
                        i--;
                        continue;
                    }
                    numval = (numval << 7) + (c & 127);
                    if (numval > (numval << (7-CCN_TT_BITS))) {
                        state = 9;
                        d->bignumval = numval;
                    }
                }
                else {
                    numval = (numval << (7-CCN_TT_BITS)) + (c >> CCN_TT_BITS);
                    c &= CCN_TT_MASK;
                    if (tagstate == 1 && c != CCN_ATTR) {
                        tagstate = 0;
                        printf(">");
                    }
                    switch (c) {
                        case CCN_BUILTIN:
                            s = ccn_decoder_push(d);
                            s->nameindex = d->stringstack->length;
                            d->schema = numval;
                            d->sstate = 0;
                            switch (numval) {
                                case CCN_PROCESSING_INSTRUCTIONS:
                                    printf("<?");
                                    break;
                                default:
                                    fprintf(stderr,
                                        "*** Warning: unrecognized builtin %lu\n",
                                        (unsigned long)numval);
                                    ccn_charbuf_append(d->stringstack,
                                        "UNKNOWN_BUILTIN",
                                        sizeof("UNKNOWN_BUILTIN"));
                                    printf("<%s code=\"%lu\"",
                                           d->stringstack->buf + s->nameindex,
                                           (unsigned long)d->schema);
                                    tagstate = 1;
                                    d->schema = CCN_UNKNOWN_BUILTIN;
                                    break;
                            }
                            state = 0;
                            break;
                        case CCN_INTVAL:
                            printf("%lu", (unsigned long)numval);
                            state = 0;
                            break;
                        case CCN_BLOB:
                            state = (numval == 0) ? 0 : 10;
                            break;
                        case CCN_UDATA:
                            state = 3;
                            if (d->schema == CCN_PROCESSING_INSTRUCTIONS) {
                                if (d->sstate > 0) {
                                    printf(" ");
                                }
                                state = 6;
                                d->sstate += 1;
                            }
                            if (numval == 0)
                                state = 0;
                            break;
                        case CCN_ATTR:
                            if (tagstate != 1) {
                                state = -__LINE__;
                                break;
                            }
                            /* FALLTHRU */
                        case CCN_TAG:
                            numval += 1; /* encoded as length-1 */
                            s = ccn_decoder_push(d);
                            ccn_charbuf_reserve(d->stringstack, numval + 1);
                            s->nameindex = d->stringstack->length;
                            state = (c == CCN_TAG) ? 4 : 5;
                            break;
                        default:
                            state = -__LINE__;
                    }
                }
                break;
            case 2: /* hex BLOB - this case currently unused */
                c = p[i++];
                printf("%02X", c);
                if (--numval == 0) {
                    state = 0;
                }
                break;
            case 3: /* utf-8 data */
                c = p[i++];
                if (--numval == 0) {
                    state = 0;
                }
                switch (c) {
                    case 0:
                        state = -__LINE__;
                        break;
                    case '&':
                        printf("&amp;");
                        break;
                    case '<':
                        printf("&lt;");
                        break;
                    case '>':
                        printf("&gt;");
                    case '"':
                        printf("&quot;");
                        break;
                    default:
                        printf("%c", c);
                }
                break;
            case 4: /* parsing tag name */
            case 5: /* parsing attribute name */
                chunk = n - i;
                if (chunk > numval) {
                    chunk = numval;
                }
                if (chunk == 0) {
                    state = -__LINE__;
                    break;
                }
                ccn_charbuf_append(d->stringstack, p + i, chunk);
                numval -= chunk;
                i += chunk;
                if (numval == 0) {
                    ccn_charbuf_append(d->stringstack, (unsigned char *)"\0", 1);
                    s = d->stack;
                    if (s == NULL ||
                        strlen((char*)d->stringstack->buf + s->nameindex) != 
                            d->stringstack->length -1 - s->nameindex) {
                        state = -__LINE__;
                        break;
                    }
                    s->itemcount = 1;
                    if (state == 4) {
                        printf("<%s", d->stringstack->buf + s->nameindex);
                        tagstate = 1;
                    }
                    else {
                        printf(" %s=\"", d->stringstack->buf + s->nameindex);
                        tagstate = 3;
                    }
                    state = 0;
                }
                break;
            case 6: /* processing instructions */
                c = p[i++];
                if (--numval == 0) {
                    state = 0;
                }
                printf("%c", c);
                break;
            case 9: /* parsing big numval - cannot be a length anymore */
                c = p[i++];
                if (c != (c & 127)) {
                    d->bignumval = (d->bignumval << 7) + (c & 127);
                }
                else {
                    d->bignumval = (d->bignumval << 3) + (c >> 4);
                    c &= 15;
                    if (tagstate == 1) {
                        tagstate = 0;
                        printf(">");
                    }
                    switch (c) {
                        case CCN_INTVAL:
                            printf("%llu", d->bignumval);
                            state = 0;
                            break;
                        default:
                            state = -__LINE__;
                    }
                }
                break;
            case 10: /* base 64 BLOB - phase 0 */
                c = p[i++];
                printf("%c", Base64[c >> 2]);
                if (--numval == 0) {
                    printf("%c==", Base64[(c & 3) << 4]);
                    state = 0;
                }
                else {
                    d->bits = (c & 3);
                    state = 11;
                }
                break;
            case 11: /* base 64 BLOB - phase 1 */
                c = p[i++];
                printf("%c", Base64[((d->bits & 3) << 4) + (c >> 4)]);
                if (--numval == 0) {
                    printf("%c=", Base64[(c & 0xF) << 2]);
                    state = 0;
                }
                else {
                    d->bits = (c & 0xF);
                    state = 12;
                }
                break;
            case 12: /* base 64 BLOB - phase 2 */
                c = p[i++];
                printf("%c%c", Base64[((d->bits & 0xF) << 2) + (c >> 6)],
                               Base64[c & 0x3F]);
                if (--numval == 0) {
                    state = 0;
                }
                else {
                    state = 10;
                }
                break;
            default:
                n = i;
        }
    }
    d->state = state;
    d->tagstate = tagstate;
    d->numval = numval;
    return(i);
}



struct cons;
struct cons {
    void *car;
    struct cons *cdr;
};

static struct cons *
last_cdr(struct cons *list)
{
    if (list != NULL) {
        while (list->cdr != NULL)
            list = list->cdr;
    }
    return (list);
}

static int
memq(struct cons *list, void *elt)
{
    for (; list != NULL; list = list->cdr) {
        if (elt == list->car)
            return(1);
    }
    return(0);
}

static struct cons *
cons(void *car, struct cons *cdr) {
    struct cons *l;
    l = calloc(1, sizeof(*l));
    l->car = car;
    l->cdr = cdr;
    return(l);
}

static void
print_schema(struct ccn_schema_node *s, enum ccn_schema_node_type container, struct cons *w)
{
    if (s == NULL) { printf("<>"); return; }
    switch (s->type) {
        case CCN_SCHEMA_LABEL:
            if (s->data == NULL || s->data->ident == NULL) {
                printf("<?!!>\n");
                break;
            }
            printf("%s", s->data->ident);
            if (s->data->code >= 0) {
                printf("[%d]", s->data->code);
            }
            printf(" ::= ");
            if (s->data->tt == CCN_TAG) {
                printf("<%s> ", s->data->ident);
            }
            print_schema(s->right, CCN_SCHEMA_SEQ, w);
            if (s->data->tt == CCN_TAG) {
                printf(" </%s>", s->data->ident);
            }
            printf("\n");
            break;
        case CCN_SCHEMA_TERMINAL:
            if (s->data == NULL || s->data->ident == NULL) {
                printf("'?!!'");
                break;
            }
            printf("'%s'", s->data->ident);
            break;
        case CCN_SCHEMA_NONTERMINAL:
            if (s->data == NULL || s->data->ident == NULL) {
                printf("<?!>");
                break;
            }
            printf("%s", s->data->ident);
            if (s->data->schema != NULL && !memq(w, s->data)) {
                last_cdr(w)->cdr = cons(s->data, NULL);
            }
            break;
        case CCN_SCHEMA_ALT:
            if (container < CCN_SCHEMA_ALT)
                printf("(");
            print_schema(s->left, CCN_SCHEMA_ALT, w);
            printf(" | ");
            print_schema(s->right, CCN_SCHEMA_ALT, w);
            if (container < CCN_SCHEMA_ALT)
                printf(")");
            break;
        case CCN_SCHEMA_SEQ:
            if (container < CCN_SCHEMA_SEQ)
                printf("(");
            print_schema(s->left, CCN_SCHEMA_SEQ, w);
            printf(" ");
            print_schema(s->right, CCN_SCHEMA_SEQ, w);
            if (container < CCN_SCHEMA_SEQ)
                printf(")");
            break;
        default: printf("<?>");
    }
}

void
ccn_print_schema(struct ccn_schema_node *s) {
    struct cons *w = cons(s == NULL ? NULL : s->data, NULL); /* work list */
    struct cons *tail;
    struct ccn_schema_data *d;
    print_schema(s, CCN_SCHEMA_LABEL, w);
    if (s->type == CCN_SCHEMA_LABEL) {
        for (tail = w->cdr; tail != NULL; tail = tail->cdr) {
            d = tail->car;
            print_schema(d->schema, CCN_SCHEMA_LABEL, w);
        }
    }
    for (; w != NULL; w = tail) {
        tail = w->cdr;
        free(w);
    }
}

struct ccn_schema_node *
ccn_schema_define(struct ccn_schema_node *defs, const char *ident, int code)
{
    struct ccn_schema_node *s;
    if (ident == NULL)
        return(NULL);
    if (defs != NULL) {
        for (s = defs; s != NULL; s = s->left) {
            if (s->type != CCN_SCHEMA_LABEL || s->data == NULL)
                return(NULL); /* bad defs */
            if (0 == strcmp(ident, (const void *)(s->data->ident)))
                return(NULL); /* duplicate name */
            if (code >= 0 && code == s->data->code)
                return(NULL); /* duplicate code */
        }
    }
    s = calloc(1, sizeof(*s));
    s->type = CCN_SCHEMA_LABEL;
    s->data = calloc(1, sizeof(*s->data));
    s->data->ident = (const void *)(strdup(ident));
    s->data->schema = s;
    s->data->code = code;
    if (defs != NULL)
        defs->left = s; /* use left link to link defs together */
    return(s);
}

struct ccn_schema_node *
ccn_schema_nonterminal(struct ccn_schema_node *label)
{
    struct ccn_schema_node *s;
    if (label == NULL || label->type != CCN_SCHEMA_LABEL ||
          label->data == NULL || label->data->schema != label)
        return(NULL);
    s = calloc(1, sizeof(*s));
    s->type = CCN_SCHEMA_NONTERMINAL;
    s->data = label->data;
    return(s);
}

struct ccn_schema_node *
ccn_schema_sanitize(struct ccn_schema_node *s) {
    if (s != NULL && s->type == CCN_SCHEMA_LABEL)
        s = ccn_schema_nonterminal(s);
    return(s);
}

struct ccn_schema_node *
ccn_schema_alt(struct ccn_schema_node *left, struct ccn_schema_node *right)
{
    struct ccn_schema_node *s;
    s = calloc(1, sizeof(*s));
    s->type = CCN_SCHEMA_ALT;
    s->left = ccn_schema_sanitize(left);
    s->right = ccn_schema_sanitize(right);
    return(s);
}

struct ccn_schema_node *
ccn_schema_seq(struct ccn_schema_node *left, struct ccn_schema_node *right)
{
    struct ccn_schema_node *s;
    left = ccn_schema_sanitize(left);
    right = ccn_schema_sanitize(right);
    if (left == NULL)
        return(right);
    if (right == NULL)
        return(left);
    s = calloc(1, sizeof(*s));
    s->type = CCN_SCHEMA_SEQ;
    s->left = left;
    s->right = right;
    return(s);
}


int main() {
    struct ccn_schema_node *goal = ccn_schema_define(NULL, "CCN", -1);
    struct ccn_schema_node *Mapping = ccn_schema_define(goal, "Mapping", 1);
    struct ccn_schema_node *Name = ccn_schema_define(goal, "Name", -1);
    struct ccn_schema_node *Component = ccn_schema_define(goal, "Component", -1);
    struct ccn_schema_node *Components = ccn_schema_define(goal, "Components", -1);
    struct ccn_schema_node *Interest = ccn_schema_define(goal, "Interest", 2);
    struct ccn_schema_node *BLOB = ccn_schema_define(goal, "BLOB", -1);
    struct ccn_schema_node *ContentAuthenticator = ccn_schema_define(goal, "ContentAuthenticator", -1);
    struct ccn_schema_node *Content = ccn_schema_define(goal, "Content", -1);
    Mapping->data->tt = CCN_TAG;
    Component->data->tt = CCN_TAG;
    Interest->data->tt = CCN_TAG;
    ContentAuthenticator->data->tt = CCN_TAG;
    Content->data->tt = CCN_TAG;
    BLOB->data->tt = CCN_BLOB;
    
    goal->right = ccn_schema_alt(Interest, Mapping);
    Mapping->right = ccn_schema_seq(Name, ccn_schema_seq(ContentAuthenticator, Content));
    Name->right = ccn_schema_sanitize(Components);
    Components->right = ccn_schema_alt(ccn_schema_seq(Component, Components), NULL);
    Interest->right = ccn_schema_seq(Name, NULL);
    Component->right = ccn_schema_sanitize(BLOB);
    Content->right = ccn_schema_sanitize(BLOB);
    ccn_print_schema(goal);
    return(0);
}
