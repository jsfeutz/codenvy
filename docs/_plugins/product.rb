module Jekyll

  class ProductDescription

    def initialize(formal_name, mini_name, name)
      @formal_name = formal_name
      @mini_name = mini_name
      @name = name
    end
  end

  class ProductTag < Liquid::Tag

    def initialize(tag_name, text, tokens)
      super
      @text = text
    end

    def render(context)

      if( !@text.nil? && !@text.empty? )
        productDescription = ProductDescription.new("Codenvy", "codenvy", "CODENVY");
        key = @text.strip;
        key[0] = '@';
        entry = productDescription.instance_variable_get(key);
        return entry;
      end
      return  "###FIXME###";
    end
  end
end

Liquid::Template.register_tag('product', Jekyll::ProductTag)