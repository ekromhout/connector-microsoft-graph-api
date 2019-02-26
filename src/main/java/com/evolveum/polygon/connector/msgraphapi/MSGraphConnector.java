

package com.evolveum.polygon.connector.msgraphapi;

import org.apache.http.impl.client.CloseableHttpClient;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@ConnectorClass(displayNameKey = "msgraphconnector.connector.display", configurationClass = MSGraphConfiguration.class)
public class MSGraphConnector implements Connector,
        CreateOp,
        DeleteOp,
        SearchOp<Filter>,
        TestOp,
        UpdateDeltaOp,
        SchemaOp,
        UpdateOp,
        UpdateAttributeValuesOp {

    private static final Log LOG = Log.getLog(MSGraphConnector.class);

    private MSGraphConfiguration configuration;

    private static final String USERS = "/users";
    private static final String GROUPS = "/groups";

    private Schema schema = null;
    private CloseableHttpClient httpclient;


    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void init(Configuration configuration) {
        LOG.info("Initialize");

        this.configuration = (MSGraphConfiguration) configuration;
        this.configuration.validate();
    }

    @Override
    public void dispose() {
        LOG.info("Dispose");

        configuration = null;
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> attributes, OperationOptions operationOptions) {
        if (objectClass == null) {
            LOG.error("Attribute of type ObjectClass not provided.");
            throw new InvalidAttributeValueException("Attribute of type ObjectClass not provided.");
        }
        if (attributes == null) {
            LOG.error("Attribute of type Set<Attribute> not provided.");
            throw new InvalidAttributeValueException("Attribute of type Set<Attribute> not provided.");
        }


        if (objectClass.is(ObjectClass.ACCOUNT_NAME)) { // __ACCOUNT__
            UserProcessing userProcessing = new UserProcessing(configuration, this);
            return userProcessing.createUser(null, attributes);


        } else if (objectClass.is(ObjectClass.GROUP_NAME)) {
            GroupProcessing groupProcessing = new GroupProcessing(configuration, this);
            return groupProcessing.createOrUpdateGroup(null, attributes);

        } else {
            throw new UnsupportedOperationException("Unsupported object class " + objectClass);
        }

    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions operationOptions) {

        if (uid.getUidValue() == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("Uid not provided or empty:").append(uid.getUidValue()).append(";");
            throw new InvalidAttributeValueException(sb.toString());
        }
        LOG.info("DELETE METHOD UID VALUE: {0}", uid.getUidValue());

        if (objectClass == null) {
            throw new InvalidAttributeValueException("ObjectClass value not provided");
        }
        LOG.info("DELETE METHOD OBJECTCLASS VALUE: {0}", objectClass);

        if (objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            UserProcessing user = new UserProcessing(configuration, this);
            user.delete(uid);

        } else if (objectClass.is(ObjectClass.GROUP_NAME)) {
            GroupProcessing group = new GroupProcessing(configuration, this);
            group.delete(uid);

        }
    }

    @Override
    public Schema schema() {
        if (this.schema == null) {
            SchemaBuilder schemaBuilder = new SchemaBuilder(MSGraphConnector.class);
            UserProcessing userProcessing = new UserProcessing(configuration, this);
            GroupProcessing group = new GroupProcessing(configuration, this);

            userProcessing.buildUserObjectClass(schemaBuilder);
            group.buildGroupObjectClass(schemaBuilder);

            return schemaBuilder.build();
        }
        return this.schema;
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass arg0, OperationOptions arg1) {
        return new FilterTranslator<Filter>() {
            @Override
            public List<Filter> translate(Filter filter) {
                return CollectionUtil.newList(filter);
            }
        };
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options) {
        LOG.info("Filter query {0}", query);
        if (objectClass == null) {
            LOG.error("Attribute of type ObjectClass not provided.");
            throw new InvalidAttributeValueException("Attribute of type ObjectClass is not provided.");
        }

        if (handler == null) {
            LOG.error("Attribute of type ResultsHandler not provided.");
            throw new InvalidAttributeValueException("Attribute of type ResultsHandler is not provided.");
        }

        if (options == null) {
            LOG.error("Attribute of type OperationOptions not provided.");
            throw new InvalidAttributeValueException("Attribute of type OperationOptions is not provided.");
        }

        LOG.info("executeQuery on {0}, filter: {1}, options: {2}", objectClass, query, options);

        if (objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            UserProcessing userProcessing = new UserProcessing(configuration, this);

            userProcessing.executeQueryForUser(query, handler, options);

        } else if (objectClass.is(ObjectClass.GROUP_NAME)) {
            GroupProcessing groupProcessing = new GroupProcessing(configuration, this);
            groupProcessing.executeQueryForGroup(query, handler, options);
        } else {
            LOG.error("Attribute of type ObjectClass is not supported.");
            throw new UnsupportedOperationException("Attribute of type ObjectClass is not supported.");
        }

    }

    @Override
    public void test() {
        ObjectProcessing objectProcessing = new ObjectProcessing(configuration, this);
        objectProcessing.test();
    }


    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objectClass, Uid uid, Set<AttributeDelta> attrsDelta, OperationOptions options) {
        if (objectClass == null) {
            LOG.error("Parameter of type ObjectClass not provided.");
            throw new InvalidAttributeValueException("Parameter of type ObjectClass not provided.");
        }

        if (uid.getUidValue() == null || uid.getUidValue().isEmpty()) {
            LOG.error("Parameter of type Uid not provided or is empty.");
            throw new InvalidAttributeValueException("Parameter of type Uid not provided or is empty.");
        }

        if (attrsDelta == null) {
            LOG.error("Parameter of type Set<AttributeDelta> not provided.");
            throw new InvalidAttributeValueException("Parameter of type Set<AttributeDelta> not provided.");
        }

        if (options == null) {
            LOG.error("Parameter of type OperationOptions not provided.");
            throw new InvalidAttributeValueException("Parameter of type OperationOptions not provided.");
        }
        LOG.info("UpdateDelta with ObjectClass: {0} , uid: {1} , attrsDelta: {2} , options: {3}  ", objectClass, uid, attrsDelta, options);
        Set<Attribute> attributeReplace = new HashSet<>();
        Set<AttributeDelta> attrsDeltaMultivalue = new HashSet<>();
        for (AttributeDelta attrDelta : attrsDelta) {
            List<Object> replaceValue = attrDelta.getValuesToReplace();
            if (replaceValue != null) {
                LOG.info("attributeReplace.add {0} {1}", AttributeBuilder.build(attrDelta.getName(), replaceValue));
                attributeReplace.add(AttributeBuilder.build(attrDelta.getName(), replaceValue));
            } else {
                LOG.info("attrsDeltaMultivalue.add {0}", attrDelta);
                attrsDeltaMultivalue.add(attrDelta);
            }
        }

        if (objectClass.is(ObjectClass.ACCOUNT_NAME)) { // __ACCOUNT__
            if (!attributeReplace.isEmpty()) {
                UserProcessing userProcessing = new UserProcessing(configuration, this);
                userProcessing.updateUser(uid, attributeReplace);
            }
            if (!attrsDeltaMultivalue.isEmpty()) {
                UserProcessing userProcessing = new UserProcessing(configuration, this);
                userProcessing.updateDeltaMultiValues(uid, attrsDeltaMultivalue, options);

            }

        } else if (objectClass.is(ObjectClass.GROUP_NAME)) { // __GROUP__
            Set<AttributeDelta> ret = new HashSet<>();
            if (!attributeReplace.isEmpty()) {
                GroupProcessing groupProcessing = new GroupProcessing(configuration, this);
                groupProcessing.createOrUpdateGroup(uid, attributeReplace);
            }
            if (!attrsDeltaMultivalue.isEmpty()) {
                GroupProcessing groupProcessing = new GroupProcessing(configuration, this);
                groupProcessing.updateDeltaMultiValuesForGroup(uid, attrsDeltaMultivalue, options);

            }
        } else {
            LOG.error("The value of the ObjectClass parameter is unsupported.");
            throw new UnsupportedOperationException("The value of the ObjectClass parameter is unsupported.");
        }
        return null;

    }

    @Override
    public Uid addAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions operationOptions) {
        if (objectClass == null) {
            LOG.error("Parameter of type ObjectClass not provided.");
            throw new InvalidAttributeValueException("Parameter of type ObjectClass not provided.");
        }

        if (uid.getUidValue() == null || uid.getUidValue().isEmpty()) {
            LOG.error("Parameter of type Uid not provided or is empty.");
            throw new InvalidAttributeValueException("Parameter of type Uid not provided or is empty.");
        }
        if (operationOptions == null) {
            LOG.error("Attribute of type OperationOptions not provided.");
        }

        if (objectClass.is(ObjectClass.GROUP_NAME)) {
            GroupProcessing groupProcessing = new GroupProcessing(configuration, this);
            groupProcessing.addToGroup(uid, attributes);

        }

        return uid;
    }

    @Override
    public Uid removeAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions operationOptions) {
        if (objectClass == null) {
            LOG.error("Parameter of type ObjectClass not provided.");
            throw new InvalidAttributeValueException("Parameter of type ObjectClass not provided.");
        }

        if (uid.getUidValue() == null || uid.getUidValue().isEmpty()) {
            LOG.error("Parameter of type Uid not provided or is empty.");
            throw new InvalidAttributeValueException("Parameter of type Uid not provided or is empty.");
        }
        if (operationOptions == null) {
            LOG.error("Attribute of type OperationOptions not provided.");
        }

        if (objectClass.is(ObjectClass.GROUP_NAME)) {
            GroupProcessing groupProcessing = new GroupProcessing(configuration, this);
            groupProcessing.removeFromGroup(uid, attributes);
        }

        return uid;
    }

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions operationOptions) {
        if (objectClass == null) {
            LOG.error("Parameter of type ObjectClass not provided.");
            throw new InvalidAttributeValueException("Parameter of type ObjectClass not provided.");
        }

        if (uid.getUidValue() == null || uid.getUidValue().isEmpty()) {
            LOG.error("Parameter of type Uid not provided or is empty.");
            throw new InvalidAttributeValueException("Parameter of type Uid not provided or is empty.");
        }
        if (operationOptions == null) {
            LOG.error("Attribute of type OperationOptions not provided.");
            throw new InvalidAttributeValueException("Attribute of type OperationOptions is not provided.");
        }

        if (objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            UserProcessing userProcessing = new UserProcessing(configuration, this);
            userProcessing.updateUser(uid, attributes);


        } else if (objectClass.is(ObjectClass.GROUP_NAME)) {
            GroupProcessing groupProcessing = new GroupProcessing(configuration, this);
            groupProcessing.createOrUpdateGroup(uid, attributes);
        }
        return uid;
    }

    public boolean isAttributeMultiValues(String objectClass, String attrName) {

        Schema schema = this.schema();
        ObjectClassInfo oci = schema.findObjectClassInfo(objectClass);
        for (AttributeInfo ai : oci.getAttributeInfo()) {
            if (ai.getName().equals(attrName)) {
                if (ai.isMultiValued()) {
                    return true;
                }
            }
        }

        return false;
    }

}